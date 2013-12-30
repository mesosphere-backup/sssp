package mesos

import org.apache.mesos.state.{Variable, ZooKeeperState}
import java.util.concurrent.TimeUnit
import mesosphere.mesos.util.FrameworkInfo
import org.apache.mesos.MesosSchedulerDriver

import play.Logger


case class Coordinator(conn: Connection) extends Runnable {
  val scheduler = new Scheduler()
  val state = new ZooKeeperState(conn.zkState.hostString, 10, TimeUnit.SECONDS,
                                 conn.zkState.path)

  /**
   * Put storage routes in the distributed store.
   * @param newState Serialized routes
   */
  def writeState(newState: String,
                 onlyIfEmpty: Boolean = false): Unit = this.synchronized {
    Logger.info(s"//mesos// Storing new configuration.")
    val padded = "    \n" + newState // To make the encoded form easier to read
    val v: Variable = state.fetch("stores").get(10, TimeUnit.SECONDS)
    if (! onlyIfEmpty || "" == new String(v.value, "UTF-8").trim) {
      state.store(v.mutate(padded.getBytes("UTF-8"))).get(10, TimeUnit.SECONDS)
      Logger.info(s"//mesos// Stored new configuration.")
    }
  }

  def readState(): String = this.synchronized {
    val v = state.fetch("stores").get(10, TimeUnit.SECONDS)
    val s = new String(v.value, "UTF-8")
    s.substring(5) // Skip padding introduced in writeState()
  }

  /**
   * Start the scheduler.
   * NB: Should be run in the background.
   */
  def run() {
    val framework = FrameworkInfo("SSSP")
    val master = conn.master
    val driver = new MesosSchedulerDriver(scheduler, framework.toProto, master)
    val status = driver.run().getValueDescriptor.getFullName
    Logger.info(s"//mesos// final status: $status")
  }
}

