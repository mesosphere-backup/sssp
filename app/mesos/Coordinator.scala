package mesos

import org.apache.mesos.state.{Variable, ZooKeeperState}
import java.util.concurrent.TimeUnit
import mesosphere.mesos.util.FrameworkInfo
import org.apache.mesos.MesosSchedulerDriver

import play.Logger

// becomeScheduler
// frameworkId

case class Coordinator(conn: Connection) {
  val log = Logger.of("mesos")

  /**
   * Start subsystems in the background.
   */
  def startSubsystems() {
    (new Thread(new Runnable {
      def run() { Scheduler.run(conn.master) }
     })).start()
    (new Thread(new Runnable {
      def run() { Executor.run(conn.master) }
     })).start()
  }
}

