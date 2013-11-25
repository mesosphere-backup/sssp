package controllers

import play.api._
import play.api.mvc._
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions._

import mesosphere.sssp._
import models._


object Application extends Controller {
  val ssspRoutes: Routes = new Routes()

  def index = Action {
    Ok(views.html.index(routeChanges, RouteChange.form))
  }

  def handleForm = Action { implicit request =>
    RouteChange.form.bindFromRequest.fold(
      errors => BadRequest(views.html.index(routeChanges, errors)),
      change => {
        change.action match {
          case "create" => {
            val region = Region.getRegion(DEFAULT_REGION)
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
        Redirect(routes.Application.index())
      }
    )
  }

  def routeChanges: Seq[RouteChange] =
    for ((path, notary) <- ssspRoutes.toSeq) yield {
      val creds = notary.credentials.getCredentials()
      RouteChange("/" + path.mkString("/"),
                  notary.bucket,
                  notary.region.getName,
                  creds.getAWSAccessKeyId,
                  creds.getAWSSecretKey,
                  "create")
    }
}
