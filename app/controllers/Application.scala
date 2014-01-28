package controllers

import com.ning.http.{client => ning}
import java.io.{Serializable, File}
import org.joda.time.format.ISODateTimeFormat
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._
import scala.util.Random
import sun.misc.BASE64Decoder

import play.api.libs.json.{Json, JsError}
import play.api.mvc._
import play.api.Play
import play.api.Play.current
import play.Logger

import models._
import util.Listener
import scala.concurrent.duration.Duration
import scala.concurrent.Await


object Application extends Controller {
  def proxy(): Option[(String, Int)] = mesos.Coordinator.executor match {
    case Some(e) if mesos.Coordinator.scheduler.isEmpty => {
      Some(e.coordinatorWebEndpoint.get)
    }
    case _ => None
  }

  def bytesIfProxying(headers: RequestHeader): BodyParser[AnyContent] =
    if (proxy().nonEmpty) {
      parse.raw.map(AnyContentAsRaw(_))
    } else parse.anyContent

  def index = Action(parse.using(bytesIfProxying)) { implicit request =>
    val action = mesos.Coordinator.executor match {
      case Some(e) if mesos.Coordinator.scheduler.isEmpty => {
        WebLogger.info("Proxying to scheduler.")
        val response = e.coordinatorWebEndpoint match {
          case None => InternalServerError
          case Some((host, port)) => {
            val config = new ning.AsyncHttpClientConfig.Builder()
              .setUserAgent("SSSPExecutor").build
            val client = new ning.AsyncHttpClient(config)
            val req = requestToNingRequest(s"$host:$port", request)
            WebLogger.info(s"Proxying: ${req.getMethod} ${req.getOriginalURI}")
            val res: ning.Response = client.prepareRequest(req).execute().get()
            WebLogger.info("Response received.")
            resultFromNingReponse(res)
          }
        }
        Action { response }
      }
      case _ => {
        WebLogger.info("Handling administrative request.")
        request.method match {
          case "GET"    => routeRootGets
          case "PUT"    => handleChanges
          case "POST"   => handleChanges
          case "HEAD"   => head
          case "DELETE" => clearRoutes
        }
      }
    }
    Await.result(action(request), Duration.Inf)
  }

  /** A sadly, a quite fancy method. Other paths are supposed to be proxied to
   *  S3, so the root needs to serve a few different purposes.
   */
  def routeRootGets: Action[AnyContent] = Action { implicit request =>
    def tsv() = {
      WebLogger.info("Displaying cluster information.")
      // Using listener.port as the unique port works because this request is
      // only answered by the single scheduler. Once this goes HA, we need to
      // find another way.
      val port  = Listener.guess().port
      val addrs = for ((host, port, _) <- endpoints()) yield s"$host:$port"
      Ok(s"sssp\t$port\t${addrs.mkString("\t")}\n")
    }
    def json() = {
      WebLogger.info("Displaying routes as JSON.")
      Ok(Json.prettyPrint(Stores.routesAsJson(passwordProtect = true)))
    }
    // I tried render{} and Accepting(...) extractors but it didn't work.
    request.acceptedTypes.take(1).map(_.toString()) match {
      case Seq("application/json") => json()
      case Seq("text/json") => json()
      case Seq("text/plain")     => tsv()
      case Seq("text/tab-separated-values")     => tsv()
      case _ => {
        if (basicAuth() == Some((Dist.user, Dist.pass))) {
          WebLogger.info("Providing tarball of running application.")
          val f: File = distSelf()
          def delete() {
            Logger.info(s"Deleting dist tarball ${f.getAbsolutePath}")
            f.delete()
          }
          Ok.sendFile(f, fileName = _ => "sssp.tgz", onClose = delete)
            .as("application/x-gzip")
        } else {
          WebLogger.info("Sending back routes as a form.")
          Ok(views.html.index(routesAsFormChanges(), FormChange.form))
        }
      }
    }
  }

  def handleChanges = Action.async { implicit request =>
    val b = request.contentType == Some("application/x-www-form-urlencoded")
    (if (b) handleForm else handleJson)(request)
  }

  def head = Action { implicit request =>
    WebLogger.info("")
    Ok("")
  }

  def clearRoutes = Action { implicit request =>
    updateRoutes(Map())
    Ok("")
  }

  def handleJson: Action[AnyContent] = Action { implicit request =>
    WebLogger.info("Handling JSON request.")
    // TODO: Handle empty bodies
    request.body.asJson.map { json =>
      json.validate[Map[String, Change]].map { updates =>
        updateRoutes(updates)
        Ok("")
      } recoverTotal(e => BadRequest("JSON Error: " + JsError.toFlatJson(e)))
    } getOrElse BadRequest("Bad JSON")
  }

  def handleForm = Action { implicit request =>
    WebLogger.info("Handling HTML form.")
    FormChange.form.bindFromRequest.fold(
      errors => BadRequest(views.html.index(routesAsFormChanges(), errors)),
      form => {
        updateRoutes(Map(form.path -> form.asChange))
        Redirect(routes.Application.index)
      }
    )
  }

  def updateRoutes(changes: Map[String, Change])(implicit request: Request[_]) {
    WebLogger.info("Altering routes.")
    Stores.updateRoutes(changes, Seq("PUT", "DELETE").contains(request.method))
    mesos.Coordinator.scheduler.foreach(_.syncRoutes())
  }

  def routesAsFormChanges(): Seq[FormChange] =
    for ((path, notary) <- Stores.routes.toSeq) yield {
      val creds = notary.credentials.getCredentials()
      FormChange("/" + path.mkString("/"),
                 notary.bucket,
                 creds.getAWSAccessKeyId.map(_ => '•'),
                 creds.getAWSSecretKey.map(_ => '•'),
                 "delete")
    }

  def notary(s: String) = Action { implicit request =>
    WebLogger.info("Storage request.")
    val path = s.split('/').filterNot(_.isEmpty).toSeq
    if (Stores.routes.isEmpty) ServiceUnavailable else {
      Stores.routes.deepestHandler(path) match {
        case Some((prefix, notary)) => {
          val relative: String = path.drop(prefix.length).mkString("/")
          WebLogger.info(s"*match* s3://${notary.bucket} /$relative")
          val (signed, seconds, date) =
            notary.sign(relative, request.method, request.headers.toMap)
          val formatted = ISODateTimeFormat.dateTimeNoMillis().withZoneUTC()
            .print(date.getTime)
          WebLogger.info(s"*expiration* ${seconds}s $formatted")
          Redirect(signed, 307)
            .withHeaders(("Cache-Control", f"max-age=${seconds - 1}"))
        }
        case None => {
          WebLogger.info(s"No store could be found for this request.")
          Forbidden
        }
      }
    }
  }

  def endpoints(): Seq[(String, Int, String)] = {
    val nodes = mesos.Coordinator.scheduler
      .map(_.nodes.single.values).getOrElse(Seq())
    val other = for (node <- nodes) yield (node.ip, node.port, node.kind)
    val me = Listener.guess()
    (me.ip, me.port, "scheduler") +: other.toSeq
  }

  def basicAuth()(implicit request: Request[_]): Option[(String, String)] = {
    val Basic = "^ *Basic +([^ ].+)$".r
    for {
      auth          <- request.headers.get("Authorization")
      Basic(base64) <- Basic.findFirstIn(auth)
      decoded       <- Option(new BASE64Decoder().decodeBuffer(base64))
      plain         <- Option(new String(decoded, "UTF-8"))
      (a, b)        <- plain.split(':') match {
                         case Array(a, b) => Some((a, b))
                         case _           => None
                       }
    } yield (a, b)
  }

  def distSelf(): File = {
    val cwd = Play.application.path.getCanonicalPath
    val tmp = f"/tmp/sssp-${Random.nextLong().abs}%016x"
    val tar = s"$tmp.tgz"
    val cmd = Seq("tar", "-C", cwd, "-czf", tar, ".")
    val ext = cmd ! ProcessLogger(Logger.info(_), Logger.warn(_))
    if (ext != 0) {
      Logger.warn(s"Bad exit code ($ext): $cmd")
      throw new RuntimeException("Failed to archive SSSP.")
    }
    Logger.info(s"Temporary dist tarball created: $tar")
    new File(tar)
  }

  def freshSelfDownloadURL(): String = {
    val listener = util.Listener.guess()
    s"http://${Dist.user}:${Dist.pass}@${listener.ip}:${listener.port}/"
  }

  def requestToNingRequest(targetHost: String,
                           request: Request[AnyContent]): ning.Request = {
    val body = request.body.asRaw.flatMap(_.asBytes()).getOrElse(Array())
    new ning.RequestBuilder(request.method)
      .setUrl("http://" + targetHost + request.path)
      .setBody(body)
      .setHeaders(request.headers.toMap.mapValues(_.asJavaCollection).asJava)
      .build()
  }

  def resultFromNingReponse(response: ning.Response): SimpleResult = {
    Logger.info("Starting resultFromNingResponse")
    val headers = for (k <- response.getHeaders.keySet().asScala)
                yield response.getHeaders.get(k).asScala.map((k,_)).toSeq
    Logger.info("Headers ready!")
    val s = Status(response.getStatusCode)(response.getResponseBodyAsBytes)
              .withHeaders(headers.toSeq.flatten:_*)
    Logger.info("Returning")
    s
  }
}

object WebLogger {
  val log = Logger.of("web")

  def trace(s: String)(implicit request: Request[_]) = log.trace(formatted(s))
  def debug(s: String)(implicit request: Request[_]) = log.debug(formatted(s))
  def info( s: String)(implicit request: Request[_]) = log.info(formatted(s))
  def warn( s: String)(implicit request: Request[_]) = log.warn(formatted(s))
  def error(s: String)(implicit request: Request[_]) = log.error(formatted(s))

  def formatted(msg: String)(implicit request: Request[_]) = if (msg != "") {
    s"#${request.id} ${request.method} ${request.path} ## $msg"
  } else {
    s"#${request.id} ${request.method} ${request.path}"
  }
}