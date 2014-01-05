package mesos

import mesosphere.mesos.util.FrameworkID
import scala.concurrent.stm._

import play.api.Play.current
import play.{Play, Logger}


case class Coordinator(conn: Connection) {
  val log = Logger.of("mesos")
  val frameworkIDs: Ref[Seq[FrameworkID]] = Ref(Seq[FrameworkID]())
  val scheduler = new Scheduler(conn)
  val executor = new Executor(conn)

  /**
   * Start subsystems in the background.
   */
  def start() {
    if (Play.application().getFile("conf/executor").exists()) {
      new Thread(executor).start()
    } else {
      new Thread(scheduler).start()
    }
  }
}

