package mesos

import com.fasterxml.jackson.core.JsonParseException
import java.util
import mesosphere.mesos.util.{FrameworkInfo, ScalarResource}
import org.apache.mesos.Protos._
import org.apache.mesos.{MesosSchedulerDriver, SchedulerDriver}
import scala.collection.JavaConverters._
import scala.concurrent.stm._

import play.Logger
import play.Logger.ALogger
import play.api.libs.json._

import mesos.Action._
import models.Stores
import org.joda.time.{DateTimeZone, DateTime}
import org.joda.time.format.{DateTimeFormat, ISODateTimeFormat}
import scala.util.Random


class Scheduler(val conn: Connection)
    extends org.apache.mesos.Scheduler with Runnable {
  private val log: ALogger = Logger.of("mesos.scheduler")
  val idList: Ref[Seq[FrameworkID]] = Ref(Seq[FrameworkID]())

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
        case ExecutorJoin(s) => {
          log.info(s"Executor joining: $s")
          val act = NewRoutes(Stores.routesAsChanges(passwordProtect = false))
          val bytes = Json.stringify(Json.toJson(act)).getBytes("UTF-8")
          driver.sendFrameworkMessage(executorId, slaveId, bytes)
        }
        case m => log.info(s"Schedulers don't handle ${m.getClass} messages.")
      } recoverTotal {
        e => log.error("JSON message not recognized: " + JsError.toFlatJson(e))
      }
    } catch {
      case e: JsonParseException => log.error("Could not parse data as JSON.")
    }
  }

  def statusUpdate(driver: SchedulerDriver, status: TaskStatus) {
    log.info(s"status update: ${status.getState.name()}")
  }

  def offerRescinded(driver: SchedulerDriver, offerId: OfferID) {}

  def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]) {
    for (offer <- offers.asScala.take(1)) {
      log.info(s"offer received: ${offer.getId.getValue}")
      val cmd = CommandInfo.newBuilder.setValue("sleep 60")
      // .addUris(CommandInfo.URI.newBuilder.setValue(
      // s"http://$adminUser:$oneTimepass@${Play.ip}:${Play.port}/sssp.tgz"
      // ))
      val id = {
        val fmt = DateTimeFormat
          .forPattern("yyyyMMDD'T'HHmmss.SSS'Z'").withZoneUTC
        f"sssp-${fmt.print(new DateTime)}-${Random.nextInt().abs}%08x"
      }

      val task = TaskInfo.newBuilder
        .setCommand(cmd)
        .setName(id)
        .setTaskId(TaskID.newBuilder.setValue(id))
        .addAllResources(resources())
        .setSlaveId(offer.getSlaveId)
        .build

      driver.launchTasks(offer.getId, Seq(task).asJava)
    }
  }

  def reregistered(driver: SchedulerDriver, masterInfo: MasterInfo) {}

  def registered(driver: SchedulerDriver,
                 frameworkId: FrameworkID,
                 masterInfo: MasterInfo) {
    log.info(s"registered as ${frameworkId.getValue}")
    idList.single.transform(_ :+ frameworkId)
    log.info(s"${idList.single.get}")

    //val request = Request.newBuilder().addAllResources(resources()).build()
    //driver.requestResources(Seq(request).asJava)
    // The above line results in a SIGSEGV (something about JNI and reflection)
  }

  def resources() = Seq(
    ScalarResource("cpus", conn.cpus),
    ScalarResource("mem", conn.mem)
  ).map(_.toProto).asJava

  /**
   * Start the scheduler.
   * NB: Should be run in the background.
   */
  def run() {
    log.info("Starting up...")
    val driver =
     new MesosSchedulerDriver(this, FrameworkInfo("SSSP").toProto, conn.master)
    val status = driver.run().getValueDescriptor.getFullName
    log.info(s"Final status: $status")
  }
}
