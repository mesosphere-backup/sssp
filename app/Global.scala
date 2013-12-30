import java.io.File
import play.api._
import play.api.libs.json._
import play.api.Play.current
import scala.io.Source

import models._

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    for (m <- Play.configuration.getConfig("mesos")) {
      val conn = mesos.Connection.fromConfig(m)
      val settingsString = Json.stringify(Json.toJson(conn))
      Logger.info(s"Mesos configuration is: $settingsString")
      val coordinator = mesos.Coordinator(conn)
      controllers.Application.coordinator = Some(coordinator)
      (new Thread(coordinator)).start()
      Logger.info("Running Mesos coordinator in the background.")
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
}
