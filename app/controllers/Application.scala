package controllers

import com.amazonaws.auth._
import com.amazonaws.internal.StaticCredentialsProvider
import mesosphere.sssp._
import org.joda.time.format.ISODateTimeFormat

import play.Logger
import play.api._
import play.api.mvc._
import models._


object Application extends Controller {
  val ssspRoutes: Routes = new Routes()

  def index() = Action {
    Ok(views.html.index(routesAsRouteChanges(), RouteChange.form))
  }

  def handleForm = Action { implicit request =>
    RouteChange.form.bindFromRequest.fold(
        errors => BadRequest(views.html.index(routesAsRouteChanges(), errors)),
        change => {
          change.action match {
            case "create" => {
              val creds = new BasicAWSCredentials(change.access, change.secret)
              val provider = new StaticCredentialsProvider(creds)
              val notary = new S3Notary(change.bucket, provider)
              val path = change.path.split('/').filterNot(_.isEmpty).toSeq
              ssspRoutes += (path -> notary)
            }
            case "delete" => {
              val path = change.path.split('/').filterNot(_.isEmpty).toSeq
              ssspRoutes -= path
            }
          }
          Redirect(routes.Application.index)
        }
    )
  }

  def routesAsRouteChanges(): Seq[RouteChange] =
    for ((path, notary) <- ssspRoutes.toSeq) yield {
      val creds = notary.credentials.getCredentials()
      RouteChange("/" + path.mkString("/"),
                  notary.bucket,
                  creds.getAWSAccessKeyId,
                  creds.getAWSSecretKey,
                  "create")
    }

  def notary(s: String) = Action { implicit request =>
    val path = s.split('/').filterNot(_.isEmpty).toSeq
    Logger.info(f"${request.id} // ${request.method} /${s}")
    ssspRoutes.deepestHandler(path) match {
      case Some((prefix, notary)) => {
        val relative: String = path.drop(prefix.length).mkString("/")
        Logger.info(f"${request.id} // /$s -> s3://${notary.bucket}/$relative")
        val (signed, seconds, date) =
          notary.sign(relative, request.method, request.headers.toMap)
        val formatted = ISODateTimeFormat.dateTimeNoMillis().withZoneUTC()
          .print(date.getTime)
        Logger.info(f"${request.id} // Expires: ${seconds}s $formatted")
        Redirect(signed, 307)
          .withHeaders(("Cache-Control", f"max-age=${seconds - 1}"))
      }
      case None => {
        Logger.info(f"${request.id} // /$s -> No mapping!")
        Forbidden
      }
    }
  }
}
