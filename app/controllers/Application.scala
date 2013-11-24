package controllers

import play.api._
import play.api.mvc._
import scala.concurrent.stm._

import models._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index(Route.active.snapshot, Route.form))
  }

  def handleForm = Action { implicit request =>
    Route.form.bindFromRequest.fold(
      errors => BadRequest(views.html.index(Route.active.snapshot, errors)),
      form => {
        form.action match {
          case "create" => {
            val route = Route(form.path, form.bucket, form.access, form.secret)
            Route.active.single += (route.path -> route)
          }
          case "delete" => Route.active.single -= form.path
        }
        //Ok(views.html.index(Route.active.snapshot, Route.form))
        Redirect(routes.Application.index())
      }
    )
  }

}
