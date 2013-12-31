package mesos

import java.util
import mesosphere.mesos.util.{FrameworkInfo, ScalarResource}
import org.apache.mesos.Protos._
import org.apache.mesos.SchedulerDriver
import scala.collection.JavaConverters._

import play.Logger


class Scheduler extends org.apache.mesos.Scheduler {
  def error(driver: SchedulerDriver, message: String) {
    Logger.error(s"//mesos// $message")
  }

  def executorLost(driver: SchedulerDriver,
                   executorId: ExecutorID,
                   slaveId: SlaveID,
                   status: Int) {}

  def slaveLost(driver: SchedulerDriver,
                slaveId: SlaveID) {}

  def disconnected(driver: SchedulerDriver) {}

  def frameworkMessage(driver: SchedulerDriver,
                       executorId: ExecutorID,
                       slaveId: SlaveID,
                       data: Array[Byte]) {}

  def statusUpdate(driver: SchedulerDriver, status: TaskStatus) {
    Logger.info(s"//mesos// status update: $status")
  }

  def offerRescinded(driver: SchedulerDriver, offerId: OfferID) {}

  def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]) {
    for (offer <- offers.asScala) {
      Logger.info(s"//mesos// offer received...")
      //
      //      val cmd = CommandInfo.newBuilder
      //        .addUris(CommandInfo.URI.newBuilder.setValue("https://gist.github.com/guenter/7470373/raw/42ed566dba6a22f1b160e9774d750e46e83b61ad/http.py"))
      //        .setValue("python http.py")
      //      val cpus = ScalarResource("cpus", 1.0)
      //      val id = "task" + System.currentTimeMillis()
      //
      //      val task = TaskInfo.newBuilder
      //        .setCommand(cmd)
      //        .setName(id)
      //        .setTaskId(TaskID.newBuilder.setValue(id))
      //        .addResources(cpus.toProto)
      //        .setSlaveId(offer.getSlaveId)
      //        .build
      //
      //      driver.launchTasks(offer.getId, List(task).asJava)
    }
  }

  def reregistered(driver: SchedulerDriver, masterInfo: MasterInfo) {}

  def registered(driver: SchedulerDriver,
                 frameworkId: FrameworkID,
                 masterInfo: MasterInfo) {}
}