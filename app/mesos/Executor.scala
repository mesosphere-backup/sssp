package mesos

import java.util
import mesosphere.mesos.util.{FrameworkInfo, ScalarResource}
import org.apache.mesos.Protos
import org.apache.mesos._
import scala.collection.JavaConverters._

import play.Logger


class Executor extends org.apache.mesos.Executor {
  private val log = Logger.of("mesos")

  def registered(driver: ExecutorDriver,
                 executorInfo: Protos.ExecutorInfo,
                 frameworkInfo: Protos.FrameworkInfo,
                 slaveInfo: Protos.SlaveInfo) {}

  def reregistered(driver: ExecutorDriver, slaveInfo: Protos.SlaveInfo) {}

  def disconnected(driver: ExecutorDriver) {}

  def launchTask(driver: ExecutorDriver, task: Protos.TaskInfo) {}

  def killTask(driver: ExecutorDriver, taskId: Protos.TaskID) {}

  def frameworkMessage(driver: ExecutorDriver, data: Array[Byte]) {}

  def shutdown(driver: ExecutorDriver) {}

  def error(driver: ExecutorDriver, message: String) {}
}
