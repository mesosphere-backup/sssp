import java.io._
import scala.io.Source

import play.api._
import play.api.libs.json._
import play.api.Play.current

import models._


object Global extends GlobalSettings {
  override def onStart(app: Application) {
    val stores = Play.configuration.getString("s3file").flatMap { p =>
      val f = new File(p)
      Option(if (f.isAbsolute) f else app.getFile(p)).filter(_.exists())
    }

    for (f <- stores) {
      Logger.info(s"Loading storage configuration from: $f")
      val s = Source.fromFile(f, "UTF-8").mkString
      val changes = Json.parse(s).validate[Map[String,AddS3]].get
      Stores.updateRoutes(changes)
    }

    val optionalMesosSettings = Play.configuration.getConfig("mesos")
                                    .filterNot(_.underlying.entrySet().isEmpty)

    for (m <- optionalMesosSettings) {
      val conn = mesos.Connection.fromConfig(m)
      Logger.info(s"Mesos master: ${conn.master}")
      val coordinator = mesos.Coordinator(conn)
      coordinator.startSubsystems()
    }
    // TODO: Don't load local config if Mesos state is non-empty.
  }

  override def onStop(app: Application) {}


}
