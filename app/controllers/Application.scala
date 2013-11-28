package controllers

import play.Logger
import play.api._
import play.api.mvc._

//import mesosphere.sssp._
import models._


object Application extends Controller {
  //val ssspRoutes: Routes = new Routes()

  def index() = Action {
    Ok(views.html.index(routesAsRouteChanges(), RouteChange.form))
  }

  def handleForm = Action { implicit request =>
//    RouteChange.form.bindFromRequest.fold(
//        errors => BadRequest(views.html.index(routesAsRouteChanges(), errors)),
//        change => {
//          change.action match {
//            case "create" => {
//              val region = s3.S3Notary.regions(change.region)
//              val creds =
//                new BasicAWSCredentialsProvider(change.access, change.secret)
//              val notary = new S3Notary(change.bucket, region, creds)
//              val path = change.path.split('/').filterNot(_.isEmpty).toSeq
//              ssspRoutes += (path -> notary)
//            }
//            case "delete" => {
//              val path = change.path.split('/').filterNot(_.isEmpty).toSeq
//              ssspRoutes -= path
//            }
//          }
//          Redirect(routes.Application.index)
//        }
//    )
    Ok("")
  }

  def routesAsRouteChanges(): Seq[RouteChange] = Seq()

//  def routesAsRouteChanges(): Seq[RouteChange] =
//    for ((path, notary) <- ssspRoutes.toSeq) yield {
//      val creds = notary.credentials.getCredentials()
//      RouteChange("/" + path.mkString("/"),
//                  notary.bucket,
//                  notary.region.getName,
//                  creds.getAWSAccessKeyId,
//                  creds.getAWSSecretKey,
//                  "create")
//    }

    def regionsList(): Seq[String] = Seq()
//  def regionsList(): Seq[String] = s3.S3Notary.regions.keys.toSeq.sortWith {
//    // Ensure classic is the first, default selection.
//    case ("classic", _) => true
//    case (_, "classic") => false
//    // Otherwise, sort alphabetically.
//    case (a, b)           => a < b
//  }

  def notary(s: String) = Action { Ok() }

//  def notary(s: String) = Action { implicit request =>
//    val path = s.split('/').filterNot(_.isEmpty).toSeq
//    Logger.info(f"${request.method} ${request.path} ${s}")
//    ssspRoutes.deepestHandler(path) match {
//      case Some((prefix, notary)) => {
//        val relative: String   = path.drop(prefix.length).mkString("/")
//        val method: HttpMethod = HttpMethod.valueOf(request.method)
//        val signed: String     = notary.sign(relative, method).toString
//        val seconds: Long      = notary.defaultExpiration.toSeconds - 1
//        Redirect(signed, 307)
//          .withHeaders(("Cache-Control", f"max-age=$seconds"))
//      }
//      case None => Forbidden
//    }
//  }
}
