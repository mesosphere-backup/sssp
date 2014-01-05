package mesos

import play.api.libs.json._
import play.api.data.validation._

import models._

sealed trait Action

object Action {
  case class ExecutorJoin(executor: String) extends Action
  object ExecutorJoin { val json = Json.format[ExecutorJoin] }

  case class NewRoutes(update: Map[String, Change]) extends Action
  object NewRoutes { val json = Json.format[NewRoutes] }

  case class RequestRoutes() extends Action
  object RequestRoutes {
    val json = {
      val err = ValidationError(s"A get action should be empty: 'get: {}'")
      val reader = (__ \ "get").read[JsObject].filter(err)(_.fields.isEmpty)
                                              .map(_ => RequestRoutes())
      val writer = new Writes[RequestRoutes] {
        def writes(r: RequestRoutes) = Json.obj("get" -> Json.obj())
      }
      Format(reader, writer)
    }
  }

  implicit val json: Format[Action] = new Format[Action] {
    def reads(json: JsValue): JsResult[Action] = json match {
      case JsObject(_) => ExecutorJoin.json.reads(json)
        .orElse(NewRoutes.json.reads(json))
        .orElse(RequestRoutes.json.reads(json))
      case _ => JsError(s"Unexpected JSON: $json")
    }

    def writes(action: Action): JsValue = action match {
      case a@ExecutorJoin(_) => ExecutorJoin.json.writes(a)
      case a@NewRoutes(_)    => NewRoutes.json.writes(a)
      case a@RequestRoutes() => RequestRoutes.json.writes(a)
    }
  }

  /**
   * Turn an action in to bytes. Used for serializing framework messages.
   * @param a An action to serialize
   * @return  Bytes of JSON-serialized action
   */
  implicit def bytes(a: Action): Array[Byte] = {
    Json.stringify(Json.toJson(a)).getBytes("UTF-8")
  }
}
