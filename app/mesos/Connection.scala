package mesos

import play.api._
import play.api.libs.json.Json


case class Connection(master: String)

object Connection {
  def fromConfig(c: Configuration): Connection = {
    (for (m  <- c.getString("master")) yield Connection(m))
      .orElse(c.getConfig("mesos").map(fromConfig))
      .get
  }

  implicit def json = Json.format[Connection]
}
