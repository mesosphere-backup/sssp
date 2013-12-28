package models

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._


case class FormChange(path:   String,
                      bucket: String,
                      access: String,
                      secret: String,
                      action: String) {
  val asChange: Change = action match {
    case "create" => AddS3(S3(bucket, access, secret))
    case "delete" => Remove()
  }
}

object FormChange {
  val form: Form[FormChange] = Form(
    mapping(
      "path"   -> of[String],
      "bucket" -> of[String],
      "access" -> of[String],
      "secret" -> of[String],
      "action" -> of[String]
    )(FormChange.apply)(FormChange.unapply)
  )
}

