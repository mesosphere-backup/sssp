package mesos

import org.apache.mesos.state.{Variable, ZooKeeperState}
import java.util.concurrent.TimeUnit
import mesosphere.mesos.util.FrameworkInfo
import org.apache.mesos.MesosSchedulerDriver

import play.Logger


case class Coordinator(connection: Connection) extends Runnable {
  val scheduler = new Scheduler()
  val state = new ZooKeeperState(connection.zkState.hostString,
                                 10, TimeUnit.SECONDS,
                                 connection.zkState.path)

  /**
   * Put storage routes in the distributed store.
   * @param newState Serialized routes
   */
  def updateState(newState: String): Unit = state.synchronized {
    Logger.info(s"//mesos// Storing new configuration.")
    val padded = "    \n" + newState // To make the encoded form easy to read.
    val v: Variable = state.fetch("stores").get(10, TimeUnit.SECONDS)
    state.store(v.mutate(padded.getBytes("UTF-8"))).get(10, TimeUnit.SECONDS)
    Logger.info(s"//mesos// Stored new configuration.")
  }

  /**
   * Start the scheduler.
   * NB: Should be run in the background.
   */
  def run() {
    val framework = FrameworkInfo("SSSP")
    val master = connection.master
    val driver = new MesosSchedulerDriver(scheduler, framework.toProto, master)
    val status = driver.run().getValueDescriptor.getFullName
    Logger.info(s"//mesos// final status: $status")
  }
}

