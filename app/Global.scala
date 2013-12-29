import java.io.File
import mesosphere.mesos.util.FrameworkInfo
import org.apache.mesos.MesosSchedulerDriver
import play.api._
import play.api.libs.json._
import play.api.Play.current
import scala.io.Source

import models._

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    for (m <- Play.configuration.getConfig("mesos")) {
      val settings = mesos.Connection.fromConfig(m)
      val settingsString = Json.stringify(Json.toJson(settings))
      Logger.info(s"Mesos configuration is: $settingsString")
      controllers.Application.scheduler = Some(new mesos.Scheduler(settings))
      startScheduler(controllers.Application.scheduler.get)
    }

    val stores = Play.configuration.getString("s3file").flatMap(p => {
      val f = new File(p)
      Option(if (f.isAbsolute) f else app.getFile(p)).filter(_.exists())
    })

    for (f <- stores) {
      Logger.info(s"Loading storage configuration from: $f")
      val s = Source.fromFile(f, "UTF-8").mkString
      val changes = Json.parse(s).validate[Map[String,AddS3]].get
      controllers.Application.updateRoutes(changes)
    }
  }

  override def onStop(app: Application) {}

  def startScheduler(scheduler: mesos.Scheduler) {
    val framework = FrameworkInfo("SSSP")
    val master = scheduler.connection.master
    val driver = new MesosSchedulerDriver(scheduler, framework.toProto, master)
    driver.run()
  }
}
