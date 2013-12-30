package controllers

import com.amazonaws.auth._
import com.amazonaws.internal.StaticCredentialsProvider
import mesosphere.sssp._
import org.joda.time.format.ISODateTimeFormat
import scala.concurrent.duration._

import play.Logger
import play.api._
import play.api.mvc._
import models._
import play.api.libs.json._
import scala.concurrent.Await


object Application extends Controller {
  val stores: Routes = new Routes()
  var coordinator: Option[mesos.Coordinator] = None

  def index() = Action { implicit request => {
      val jsonRequest = request.contentType
        .map(Seq("application/json", "text/json").contains(_))
      if (jsonRequest.getOrElse(false)) {
        Ok(Json.prettyPrint(routesAsJson()))
      } else {
        Ok(views.html.index(routesAsFormChanges(), FormChange.form))
      }
    }
  }

  def handleChanges = Action { implicit request => {
      // Chaining Actions is rather awkward.
      val res = request.contentType match {
        case Some("application/x-www-form-urlencoded") => handleForm(request)
        case _                                         => handleJson(request)
      }
      Await.result(res, Duration.Inf)
    }
  }

  def head = Action { Ok("") }

  def clearRoutes = Action {
    stores.clear()
    Ok("")
  }

  def handleJson: Action[AnyContent] = Action { implicit request =>
    // TODO: Handle empty bodies
    request.body.asJson.map { json =>
      json.validate[Map[String, Change]].map { updates =>
        updateRoutes(updates, Some(request))
        Ok("")
      } recoverTotal(e => BadRequest("JSON Error: " + JsError.toFlatJson(e)))
    } getOrElse BadRequest("Bad JSON")
  }

  def handleForm = Action { implicit request =>
    FormChange.form.bindFromRequest.fold(
      errors => BadRequest(views.html.index(routesAsFormChanges(), errors)),
      form => {
        updateRoutes(Map(form.path -> form.asChange), Some(request))
        Redirect(routes.Application.index)
      }
    )
  }

  def updateRoutes(map: Map[String, Change],
                   request: Option[Request[_]] = None,
                   clear: Boolean = false) = {
    // PUT requests result in an overwrite, not an update.
    if (request.map(_.method == "PUT").getOrElse(clear)) stores.clear()

    // TODO: Wrap everything, including clear(), in an STM transaction.
    for ((s, change) <- map) {
      val path = s.split('/').filterNot(_.isEmpty).toSeq
      val msg = s"//routes// $s -> ${change.summary}"
      Logger.info(request.map(r => s"${r.id} $msg").getOrElse(msg))

      change match {
        case Remove() => stores -= path
        case AddS3(S3(bucket, access, secret)) => {
          val creds = new BasicAWSCredentials(access, secret)
          val provider = new StaticCredentialsProvider(creds)
          stores += (path -> new S3Notary(bucket, provider))
        }
      }
    }

    syncRoutesToCoordinator()
  }

  def syncRoutesFromCoordinator() = for (c <- coordinator) {
    Json.parse(c.readState()).validate[Map[String, Change]].map { updates =>
      updateRoutes(updates, clear = true)
    } recoverTotal { e =>
      Logger.error("Bad JSON from coordinator: " + JsError.toFlatJson(e))
    }
  }

  def syncRoutesToCoordinator(onlyIfEmpty: Boolean = false) =
    for (c <- coordinator) {
      val json = routesAsJson(passwordProtect = false)
      c.writeState(Json.stringify(json), onlyIfEmpty)
    }

  def routesAsFormChanges(): Seq[FormChange] =
    for ((path, notary) <- stores.toSeq) yield {
      val creds = notary.credentials.getCredentials()
      FormChange("/" + path.mkString("/"),
                 notary.bucket,
                 creds.getAWSAccessKeyId.map(_ => '•'),
                 creds.getAWSSecretKey.map(_ => '•'),
                 "delete")
    }

  def routesAsChanges(passwordProtect: Boolean = true): Map[String, Change] =
    for ((path, notary) <- stores.toMap) yield {
      val creds = notary.credentials.getCredentials()
      val s3 = if (passwordProtect) {
        S3(notary.bucket, creds.getAWSAccessKeyId.map(_ => '•'),
                          creds.getAWSSecretKey.map(_ => '•'))
      } else {
        S3(notary.bucket, creds.getAWSAccessKeyId, creds.getAWSSecretKey)
      }
      (("/" + path.mkString("/")) -> AddS3(s3))
    }

  def routesAsJson(passwordProtect: Boolean = true): JsValue = {
    Json.toJson(routesAsChanges(passwordProtect).mapValues(Json.toJson(_)))
  }

  def notary(s: String) = Action { implicit request =>
    val path = s.split('/').filterNot(_.isEmpty).toSeq
    Logger.info(s"${request.id} // ${request.method} /$s")
    stores.deepestHandler(path) match {
      case Some((prefix, notary)) => {
        val relative: String = path.drop(prefix.length).mkString("/")
        Logger.info(s"${request.id} // /$s -> s3://${notary.bucket}/$relative")
        val (signed, seconds, date) =
          notary.sign(relative, request.method, request.headers.toMap)
        val formatted = ISODateTimeFormat.dateTimeNoMillis().withZoneUTC()
          .print(date.getTime)
        Logger.info(s"${request.id} // Expires: ${seconds}s $formatted")
        Redirect(signed, 307)
          .withHeaders(("Cache-Control", f"max-age=${seconds - 1}"))
      }
      case None => {
        Logger.info(s"${request.id} // /$s -> No mapping!")
        Forbidden
      }
    }
  }
}
