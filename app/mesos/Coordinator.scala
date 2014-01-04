package mesos

import mesosphere.mesos.util.FrameworkID
import scala.concurrent.stm._

import play.Logger

// becomeScheduler
// frameworkId

case class Coordinator(conn: Connection) {
  val log = Logger.of("mesos")
  val frameworkIDs: Ref[Seq[FrameworkID]] = Ref(Seq[FrameworkID]())
  val scheduler = new Scheduler(conn)
  val executor = new Executor(conn)

  /**
   * Start subsystems in the background.
   */
  def startSubsystems() {
    new Thread(scheduler).start()
    // new Thread(executor).start()
  }
}

