import play.api._


case class MesosConnection( master: String, confRoot: String)

object MesosConnection {
  def apply(c: Configuration): MesosConnection = (
    { for { m <- c.getString("master")
            c <- c.getString("conf_path") } yield MesosConnection(m, c)
    } orElse c.getConfig("mesos").map(apply)
  ).get
}
