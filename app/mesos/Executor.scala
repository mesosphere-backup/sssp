package mesos

import com.fasterxml.jackson.core.JsonParseException
import org.apache.mesos._

import play.Logger
import play.api.libs.json.{JsError, Json}

import models.Stores
import mesos.Message._


class Executor(val conn: Conf)
    extends org.apache.mesos.Executor with Runnable {
  private val log = Logger.of("mesos.executor")
  var coordinatorWebEndpoint: Option[(String, Int)] = None

  def registered(driver: ExecutorDriver,
                 executorInfo: Protos.ExecutorInfo,
                 frameworkInfo: Protos.FrameworkInfo,
                 slaveInfo: Protos.SlaveInfo) {
    log.info(s"registered as: ${executorInfo.getExecutorId.getValue}")
    val listener = util.Listener.guess()
    val act = ExecutorJoin(executorInfo.getExecutorId.getValue,
                           listener.hostname,
                           listener.ip,
                           listener.port)
    driver.sendFrameworkMessage(act)
  }

  def reregistered(driver: ExecutorDriver, slaveInfo: Protos.SlaveInfo) {}

  def disconnected(driver: ExecutorDriver) {}

  def launchTask(driver: ExecutorDriver, task: Protos.TaskInfo) {}

  def killTask(driver: ExecutorDriver, taskId: Protos.TaskID) {}

  def frameworkMessage(driver: ExecutorDriver, data: Array[Byte]) = try {
    Json.parse(new String(data, "UTF-8")).validate[Message].map {
      case NewCoordinator(host, port) => {
        coordinatorWebEndpoint = Some((host, port))
      }
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

  def shutdown(driver: ExecutorDriver) {}

  def error(driver: ExecutorDriver, message: String) {}

  /**
   * Start the executor.
   * NB: Should be run in the background.
   */
  def run() {
    log.info("Starting up...")
    val driver = new MesosExecutorDriver(this)
    val status = driver.run().getValueDescriptor.getFullName
    log.info(s"final status: $status")
  }
}