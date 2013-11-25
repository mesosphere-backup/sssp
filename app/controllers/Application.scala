package controllers

import play.api._
import play.api.mvc._

import mesosphere.sssp._
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
            val region = S3Notary.regions(change.region)
            val creds =
              new BasicAWSCredentialsProvider(change.access, change.secret)
            val notary = new S3Notary(change.bucket, region, creds)
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
                  notary.region.getName,
                  creds.getAWSAccessKeyId,
                  creds.getAWSSecretKey,
                  "create")
    }

  def regionsList(): Seq[String] = S3Notary.regions.keys.toSeq.sortWith {
    // Ensure us-east-1 is the first, default selection.
    case ("us-east-1", _) => true
    case (_, "us-east-1") => false
    // Sort backwards so the other US regions show up at the beginning, next
    // to us-east-1. It looks more natural this way.
    case (a, b)           => a > b
  }
}
