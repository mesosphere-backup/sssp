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
  val ssspRoutes: Routes = new Routes()

  def index() = Action {
    Ok(views.html.index(routesAsFormChanges(), FormChange.form))
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
                   request: Option[Request[_]] = None) =
    for ((s, change) <- map) {
      val path = s.split('/').filterNot(_.isEmpty).toSeq
      val msg = s"//routes// $s -> ${change.summary}"
      Logger.info(request.map(r => s"${r.id} $msg").getOrElse(msg))

      change match {
        case Remove() => ssspRoutes -= path
        case AddS3(S3(bucket, access, secret)) => {
          val creds = new BasicAWSCredentials(access, secret)
          val provider = new StaticCredentialsProvider(creds)
          ssspRoutes += (path -> new S3Notary(bucket, provider))
        }
      }
    }

  def routesAsFormChanges(): Seq[FormChange] =
    for ((path, notary) <- ssspRoutes.toSeq) yield {
      val creds = notary.credentials.getCredentials()
      FormChange("/" + path.mkString("/"),
                 notary.bucket,
                 creds.getAWSAccessKeyId.map(_ => '•'),
                 creds.getAWSSecretKey.map(_ => '•'),
                 "delete")
    }

  // Presently unused.
  def routesAsChanges(): Map[String, AddS3] =
    for ((path, notary) <- ssspRoutes.toMap) yield {
      val creds = notary.credentials.getCredentials()
      val s3 = S3(notary.bucket, creds.getAWSAccessKeyId.map(_ => '•'),
                                 creds.getAWSSecretKey.map(_ => '•'))
      (("/" + path.mkString("/")) -> AddS3(s3))
    }

  def notary(s: String) = Action { implicit request =>
    val path = s.split('/').filterNot(_.isEmpty).toSeq
    Logger.info(s"${request.id} // ${request.method} /$s")
    ssspRoutes.deepestHandler(path) match {
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
