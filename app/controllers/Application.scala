package controllers

import java.io.{File, PipedOutputStream, PipedInputStream, InputStream}
import org.joda.time.format.ISODateTimeFormat
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process._

import play.api.Play
import play.Logger
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee._

import models._
import scala.util.Random


object Application extends Controller {
  def index() = Action { implicit request =>
    if (request.contentType
               .map(Seq("application/json", "text/json").contains(_))
               .getOrElse(false)) {
      WebLogger.info("Displaying routes as JSON.")
      Ok(Json.prettyPrint(Stores.routesAsJson(passwordProtect = true)))
    } else {
      if (request.headers.toMap.contains("GetDistribution")) {
        WebLogger.info("Providing tarball of running application.")
        val f: File = tgzSelf()
        Ok.sendFile(f, fileName = _ => "sssp.tgz", onClose = () => f.delete())
          .as("application/x-gzip")
      } else {
        WebLogger.info("Sending back routes as a form.")
        Ok(views.html.index(routesAsFormChanges(), FormChange.form))
      }
    }
  }

  def handleChanges = Action { implicit request =>
    val b = request.contentType == Some("application/x-www-form-urlencoded") &&
            request.body.asText.filter(_.take(1) == "{").isEmpty
    Await.result((if (b) handleForm else handleJson)(request), Duration.Inf)
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
    // TODO: Handle empty bodies
    request.body.asJson.map { json =>
      json.validate[Map[String, Change]].map { updates =>
        updateRoutes(updates)
        Ok("")
      } recoverTotal(e => BadRequest("JSON Error: " + JsError.toFlatJson(e)))
    } getOrElse BadRequest("Bad JSON")
  }

  def handleForm = Action { implicit request =>
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
    if (Stores.routes.isEmpty) ServiceUnavailable else
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

  def tgzSelf(): File = {
    import play.api.Play.current
    val cwd = Play.application.path.getCanonicalPath
    val tmp = s"/tmp/sssp-${Random.nextLong().abs}%016x.tgz"
    val tar = Seq("tar", "-C", cwd, "-czf", tmp, ".")
    val ext = tar ! ProcessLogger(Logger.info(_), Logger.warn(_))
    if (ext != 0) Logger.warn(s"Bad exit code ($ext): $tar")
    new File(tmp)
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