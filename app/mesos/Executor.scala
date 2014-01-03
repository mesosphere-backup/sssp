package mesos

import com.fasterxml.jackson.core.JsonParseException
import org.apache.mesos._
import org.joda.time._
import org.joda.time.format.ISODateTimeFormat
import scala.util.Random

import play.Logger
import play.api.libs.json.{JsError, Json}

import models.Stores
import mesos.Action._


class Executor extends org.apache.mesos.Executor {
  private val log = Logger.of("mesos.executor")
  val id = {
    val now = new DateTime(DateTimeZone.UTC)
    val fmt = ISODateTimeFormat.basicDateTime().withZoneUTC().print(now)
    f"$fmt#${Random.nextInt().abs}%08x"
  }

  def registered(driver: ExecutorDriver,
                 executorInfo: Protos.ExecutorInfo,
                 frameworkInfo: Protos.FrameworkInfo,
                 slaveInfo: Protos.SlaveInfo) {}

  def reregistered(driver: ExecutorDriver, slaveInfo: Protos.SlaveInfo) {}

  def disconnected(driver: ExecutorDriver) {}

  def launchTask(driver: ExecutorDriver, task: Protos.TaskInfo) {}

  def killTask(driver: ExecutorDriver, taskId: Protos.TaskID) {}

  def frameworkMessage(driver: ExecutorDriver, data: Array[Byte]) {
    try {
      Json.parse(new String(data, "UTF-8")).validate[Action].map {
        case NewRoutes(updates) => {
          log.info("Updating routes.")
          Stores.updateRoutes(updates)
        }
        case RequestRoutes() => {
          log.info("Responding to request for up-to-date routes.")
          val json = Stores.routesAsJson(passwordProtect = false)
          val bytes = Json.stringify(json).getBytes("UTF-8")
          driver.sendFrameworkMessage(bytes)
        }
        case m => log.info(s"Executors don't handle ${m.getClass} messages.")
      } recoverTotal {
        e => log.error("JSON message not recognized: " + JsError.toFlatJson(e))
      }
    } catch {
      case e: JsonParseException => log.error("Could not parse data as JSON.")
    }
  }

  def shutdown(driver: ExecutorDriver) {}

  def error(driver: ExecutorDriver, message: String) {}
}

object Executor {
  /**
   * Start the executor.
   * NB: Should be run in the background.
   */
  def run(master: String) {
    Logger.of("mesos.executor").info("Starting up...")
    val driver = new MesosExecutorDriver(new Executor())
    val status = driver.run().getValueDescriptor.getFullName
    Logger.of("mesos.executor").info(s"final status: $status")
  }
}