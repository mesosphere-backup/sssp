package mesos

import java.util
import mesosphere.mesos.util.{FrameworkInfo, ScalarResource}
import org.apache.mesos.Protos._
import org.apache.mesos.SchedulerDriver
import scala.collection.JavaConverters._

import play.Logger
import play.Logger.ALogger
import play.api.libs.json._
import com.fasterxml.jackson.core.JsonParseException
import mesos.Action.{RequestRoutes, NewRoutes}
import models.Stores


class Scheduler extends org.apache.mesos.Scheduler {
  private val log: ALogger = Logger.of("mesos")

  def error(driver: SchedulerDriver, message: String) {
    log.error(s"$message")
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
                       data: Array[Byte]) {
    try {
      Json.parse(new String(data, "UTF-8")).validate[Action].map {
        case NewRoutes(updates) => Stores.updateRoutes(updates)
        case RequestRoutes() => {
          val json = Stores.routesAsJson(passwordProtect = false)
          val bytes = Json.stringify(json).getBytes("UTF-8")
          driver.sendFrameworkMessage(executorId, slaveId, bytes)
        }
      } recoverTotal {
        e => log.error("JSON message not recognized: " + JsError.toFlatJson(e))
      }
    } catch {
      case e: JsonParseException => log.error("Could not parse data as JSON.")
    }
  }

  def statusUpdate(driver: SchedulerDriver, status: TaskStatus) {
    log.info(s"status update: $status")
  }

  def offerRescinded(driver: SchedulerDriver, offerId: OfferID) {}

  def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]) {
    for (offer <- offers.asScala) {
      log.info(s"offer received...")
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