package models

import scala.concurrent.stm._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._


case class Route(path: String, bucket: String, access: String, secret: String)

object Route {
  val active: TMap[String, Route] = TMap()
  val form: Form[Route] = Form(
    mapping(
      "path"   -> of[String],
      "bucket" -> of[String],
      "access" -> of[String],
      "secret" -> of[String]
    )(Route.apply)(Route.unapply)
  )
}
