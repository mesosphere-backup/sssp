package mesos

import mesosphere.mesos.util.FrameworkID
import scala.concurrent.stm._

import play.api.Play
import play.api.Play.current
import play.Logger


object Coordinator {
  val log = Logger.of("mesos")
  val frameworkIDs: Ref[Seq[FrameworkID]] = Ref(Seq[FrameworkID]())
  var conf: Option[Conf] = None
  var scheduler: Option[Scheduler] = None
  var executor: Option[Executor] = None

  /**
   * Start subsystems in the background.
   */
  def start() = conf match {
    case Some(conf) => {
      val dir = "conf/mesos/"
      val executorFile = Play.application.getFile(s"$dir/executor").exists()
      val schedulerFile = Play.application.getFile(s"$dir/scheduler").exists()
      if (executorFile) {
        val e = new Executor(conf)
        new Thread(e).start()
        executor = Some(e)
      }
      if (schedulerFile || ! executorFile) {
        val s = new Scheduler(conf)
        new Thread(s).start()
        scheduler = Some(s)
      }
    }
    case None => throw new RuntimeException(
      "It is not possible to start the coordinator without Mesos settings."
    )
  }
}

