package mesos

import play.api._
import play.api.libs.json.Json


case class Conf(master: String, nodes: Int, cpus: Double, mem: Int)

object Conf {
  def fromConfig(c: Configuration): Option[Conf] = for {
    master <- c.getString("master").orElse(Some("zk://localhost:2181/mesos"))
    nodes  <- c.getInt("workers").orElse(Some(2))
    mem    <- c.getInt("mem").orElse(Some(256))
    cpus   <- c.getDouble("cpus").orElse(Some(0.5))
    admin  <- c.getString("admin").orElse(Some("sssp-mesos"))
  } yield Conf(master, nodes, cpus, mem)

  implicit def json = Json.format[Conf]
}
