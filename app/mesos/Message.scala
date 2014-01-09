package mesos

import play.api.libs.json._
import play.api.data.validation._

import models._


sealed trait Message

object Message {
  case class ExecutorJoin(id: String, hostname: String, ip: String, port: Int)
    extends Message
  object ExecutorJoin { val json = Json.format[ExecutorJoin] }

  case class NewCoordinator(host: String, port: Int) extends Message
  object NewCoordinator { val json = Json.format[NewCoordinator] }

  case class NewRoutes(update: Map[String, Change]) extends Message
  object NewRoutes { val json = Json.format[NewRoutes] }

  case class RequestRoutes() extends Message
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

  implicit val json: Format[Message] = new Format[Message] {
    def reads(json: JsValue): JsResult[Message] = json match {
      case JsObject(_) => JsError() // For orElse chaining.
        .orElse(ExecutorJoin.json.reads(json))
        .orElse(NewCoordinator.json.reads(json))
        .orElse(NewRoutes.json.reads(json))
        .orElse(RequestRoutes.json.reads(json))
      case _ => JsError(s"Unexpected JSON: $json")
    }

    def writes(action: Message): JsValue = action match {
      case a@ExecutorJoin(_, _, _, _) => ExecutorJoin.json.writes(a)
      case a@NewCoordinator(_, _)     => NewCoordinator.json.writes(a)
      case a@NewRoutes(_)             => NewRoutes.json.writes(a)
      case a@RequestRoutes()          => RequestRoutes.json.writes(a)
    }
  }

  /**
   * Turn an action in to bytes. Used for serializing framework messages.
   * @param a An action to serialize
   * @return  Bytes of JSON-serialized action
   */
  implicit def bytes(a: Message): Array[Byte] = {
    Json.stringify(Json.toJson(a)).getBytes("UTF-8")
  }
}
