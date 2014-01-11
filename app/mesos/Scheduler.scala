package mesos

import util.Listener
import com.fasterxml.jackson.core.JsonParseException
import java.util
import mesosphere.mesos.util.{FrameworkInfo, ScalarResource}
import org.apache.mesos.Protos._
import org.apache.mesos.{MesosSchedulerDriver, SchedulerDriver}
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import scala.collection.JavaConverters._
import scala.concurrent.stm._
import scala.io.Source
import scala.util.Random

import play.api.libs.json._
import play.api.Play
import play.api.Play.current
import play.Logger
import play.Logger.ALogger

import mesos.Message._
import models.Stores


class Scheduler(val conn: Conf)
    extends org.apache.mesos.Scheduler with Runnable {
  private val log: ALogger = Logger.of("mesos.scheduler")
  val idList: Ref[Seq[FrameworkID]] = Ref(Seq[FrameworkID]())
  val nodes: TMap[String, Node] = TMap()
  val deadNodes: TMap[String, Node] = TMap() // TODO: Track a few dead nodes.
  var driver: SchedulerDriver = null

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
      Json.parse(new String(data, "UTF-8")).validate[Message].map {
        case ExecutorJoin(id, hostname, ip, port) => {
          log.info(s"Executor joining: $ip:$port")
          val (id, slave) = (executorId.getValue, slaveId.getValue)
          nodes.single += (id -> ExecutorNode(id, hostname, ip, port, slave))
          val me = Listener.guess()
          val routeUpdates = Stores.routesAsChanges(passwordProtect = false)
          val ms = Seq(NewCoordinator(me.ip, me.port), NewRoutes(routeUpdates))
          for (m <- ms) driver.sendFrameworkMessage(executorId, slaveId, m)
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
    val id: String = status.getTaskId.getValue
    val state = status.getState
    def lost() {
      nodes.single -= id
      log.warn(s"Task $id // ${state.name()}")
    }

    status.getState match {
      case TaskState.TASK_FAILED   => lost()
      case TaskState.TASK_FINISHED => lost()
      case TaskState.TASK_KILLED   => lost()
      case TaskState.TASK_LOST     => lost()
      case _                       => {}
    }
  }

  def offerRescinded(driver: SchedulerDriver, offerId: OfferID) {}

  def resourceOffers(driver: SchedulerDriver, offers: util.List[Offer]) {
    for (offer <- offers.asScala.take(conn.nodes - nodes.single.size)) {
      log.info(s"handling offer: ${offer.getId.getValue}")

      val port: Long = offer.getResourcesList.asScala
        .filter(_.getName == "ports")
        .filter(_.getType == Value.Type.RANGES)
        .toSeq
        .map(_.getRanges.getRangeList.asScala.map(_.getBegin).take(1))
        .take(1).flatten
        .head // It is an error if there are no ports available...

      val dist = controllers.Application.freshSelfDownloadURL()
      val env = s"export DIST=$dist PORT=$port SSSP_MESOS_MODE=executor"
      val script = Play.application.getFile("conf/sssp-for-mesos")
      val cmd = CommandInfo.newBuilder
        .setValue(env + " &&\n" + Source.fromFile(script, "UTF-8").mkString)

      // NB: This is the task ID and the executor ID.
      val id = {
        val fmt = DateTimeFormat
          .forPattern("yyyyMMDD'T'HHmmss.SSS'Z'").withZoneUTC
        f"sssp-${fmt.print(new DateTime)}-${Random.nextInt().abs}%08x"
      }

      val executor = ExecutorInfo.newBuilder()
        .setCommand(cmd).setExecutorId(ExecutorID.newBuilder().setValue(id))

      val portResource = Resource.newBuilder()
        .setName("ports")
        .setType(Value.Type.RANGES)
        .setRanges(Value.Ranges.newBuilder()
        .addRange(Value.Range.newBuilder()
        .setBegin(port).setEnd(port)))
        .build

      val task = TaskInfo.newBuilder
        .setExecutor(executor)
        .setName(id)
        .setTaskId(TaskID.newBuilder.setValue(id))
        .addAllResources(resources())
        .addResources(portResource)
        .setSlaveId(offer.getSlaveId)
        .build

      driver.launchTasks(offer.getId, Seq(task).asJava)
    }
  }

  def reregistered(driver: SchedulerDriver, masterInfo: MasterInfo) {
    log.info(s"reregistered")
    this.driver = driver
  }

  def registered(driver: SchedulerDriver,
                 frameworkId: FrameworkID,
                 masterInfo: MasterInfo) {
    log.info(s"registered as ${frameworkId.getValue}")
    idList.single.transform(_ :+ frameworkId)
    this.driver = driver
  }

  def resources(): util.List[Resource] = Seq(
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

  // TODO: Spawn in background, to handle live cluster size reduction.
  def cull() {
    for (id <- nodes.single.keys.take(nodes.single.size - conn.nodes))
      driver.killTask(TaskID.newBuilder().setValue(id).build())
  }

  def syncRoutes() {
    val act = NewRoutes(Stores.routesAsChanges(passwordProtect = false))
    for ((_, ExecutorNode(id, _, _, _, slave)) <- nodes.single)
      driver.sendFrameworkMessage(ExecutorID.newBuilder().setValue(id).build(),
                                  SlaveID.newBuilder().setValue(slave).build(),
                                  act)
  }
}
