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
      val conn = mesos.Conf.fromConfig(m).get
      Logger.info(s"Mesos master: ${conn.master}")
      mesos.Coordinator.conf = Some(conn)
      mesos.Coordinator.start()
    }
  }

  override def onStop(app: Application) {}


}
