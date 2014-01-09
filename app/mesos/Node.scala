package mesos


sealed trait Node {
  val id: String
  val hostname: String
  val ip: String
  val port: Int
  val kind: String
}

case class SchedulerNode(id: String, hostname: String, ip: String, port: Int)
  extends Node { val kind = "scheduler" }

case class ExecutorNode(id: String,
                        hostname: String,
                        ip: String,
                        port: Int,
                        slave: String)
  extends Node { val kind = "executor" }