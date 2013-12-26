package models

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._


case class RouteChange(path:   String,
                       bucket: String,
                       access: String,
                       secret: String,
                       action: String) {
}

object RouteChange {
  val form: Form[RouteChange] = Form(
    mapping(
      "path"   -> of[String],
      "bucket" -> of[String],
      "access" -> of[String],
      "secret" -> of[String],
      "action" -> of[String]
    )(RouteChange.apply)(RouteChange.unapply)
  )
}
