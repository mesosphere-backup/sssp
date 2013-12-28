package models

import play.api.libs.json._
import play.api.data.validation._


sealed trait Change {
  val summary: String
}

case class Remove() extends Change { val summary = Change.removeString }
case class AddS3(s3: S3) extends Change { val summary = s"s3://${s3.bucket}" }

case class S3(bucket: String, access: String, secret: String)

object Change {
  val removeString = "*delete*"
  implicit val jsonRemove = {
    val s = "*delete*"
    val err = ValidationError(s"Expected string '$s' for route removal.")
    val reader = __.read[String].filter(err)(_ == s).map(_ => Remove())
    val writer = new Writes[Remove] { def writes(r: Remove) = JsString(s) }
    Format(reader, writer)
  }

  implicit val jsonS3 = Json.format[S3]

  implicit val jsonAddS3 = Json.format[AddS3]

  implicit val jsonChange: Format[Change] = new Format[Change] {
    def reads(json: JsValue): JsResult[Change] = json match {
      case JsString(_) => json.validate[Remove]
      case JsObject(_) => json.validate[AddS3]
      case _ => JsError(s"Unexpected JSON: $json")
    }

    def writes(change: Change): JsValue = change match {
      case Remove() => jsonRemove.writes(change.asInstanceOf[Remove])
      case AddS3(_) => jsonAddS3.writes(change.asInstanceOf[AddS3])
    }
  }

}

