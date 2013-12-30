package mesos

import play.api._
import play.api.libs.json.Json


case class Connection(master: String, zkState: ZKConnection)

case class ZKConnection(hostString: String, path: String)

object ZKConnection {
  implicit def json = Json.format[ZKConnection]
}

object Connection {
  def fromConfig(c: Configuration): Connection = (
    { for { m  <- c.getString("master")
            zk <- c.getConfig("state").flatMap(_.getString("zk"))
      } yield {
        val ZKURL = "^zk://([^/]+)/([^/].*)$".r
        zk match {
          case ZKURL(hosts, path) =>
            Connection(m, ZKConnection(hosts, '/' +: path))
        }
      }
    } orElse c.getConfig("mesos").map(fromConfig)
  ).get

  implicit def json = Json.format[Connection]
}
