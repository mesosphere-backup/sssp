package mesos

import java.util
import mesosphere.mesos.util.{FrameworkInfo, ScalarResource}
import org.apache.mesos.Protos._
import org.apache.mesos._
import scala.collection.JavaConverters._

import play.Logger


class Executor extends org.apache.mesos.Executor {
  def registered(driver: ExecutorDriver, executorInfo: ExecutorInfo, frameworkInfo: Protos.FrameworkInfo, slaveInfo: SlaveInfo): Unit = ???

  def reregistered(driver: ExecutorDriver, slaveInfo: SlaveInfo): Unit = ???

  def disconnected(driver: ExecutorDriver): Unit = ???

  def launchTask(driver: ExecutorDriver, task: TaskInfo): Unit = ???

  def killTask(driver: ExecutorDriver, taskId: TaskID): Unit = ???

  def frameworkMessage(driver: ExecutorDriver, data: Array[Byte]): Unit = ???

  def shutdown(driver: ExecutorDriver): Unit = ???

  def error(driver: ExecutorDriver, message: String): Unit = ???
}
