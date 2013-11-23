package controllers

import play.api._
import play.api.mvc._
import scala.concurrent.stm._

import models._

object Application extends Controller {

  def index = Action {
    Ok(views.html.index(Route.active.snapshot, Route.form))
  }

  def newRoute = Action { implicit request =>
    Route.form.bindFromRequest.fold(
      errors => BadRequest(views.html.index(Route.active.snapshot, errors)),
      route => {
        Route.active.single += (route.path -> route)
        Ok(views.html.index(Route.active.snapshot, Route.form))
      }
    )
  }

}