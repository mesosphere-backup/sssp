package mesos

import play.api._
import play.api.libs.json.Json


case class Connection(master: String, cpus: Double, mem: Int)

object Connection {
  def fromConfig(c: Configuration): Connection = { for {
    master <- c.getString("master")
    mem    <- c.getInt("mem").orElse(Some(256))
    cpus   <- c.getDouble("cpus").orElse(Some(0.5))
    admin  <- c.getString("admin").orElse(Some("sssp-mesos"))
  } yield Connection(master, cpus, mem) }.get

  implicit def json = Json.format[Connection]
}
