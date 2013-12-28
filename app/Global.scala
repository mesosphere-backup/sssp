import java.io.File
import models.Change
import play.api._
import play.api.libs.json._
import play.api.Play.current
import scala.io.Source

object Global extends GlobalSettings {
  override def onStart(app: Application) {
    val mesos = Play.configuration.getConfig("mesos")
    val stores = Play.configuration.getString("stores_file").flatMap(p => {
      val f = new File(p)
      Option(if (f.isAbsolute) f else app.getFile(p)).filter(_.exists())
    })

    mesos.map(MesosConnection(_))
         .map(m => Logger.info(s"Mesos configuration is: $m"))
    for (f <- stores) {
      Logger.info(s"Loading local stores from: $f")
      val s = Source.fromFile(f, "UTF-8").mkString
      val changes = Json.parse(s).validate[Map[String,Change]].get
      controllers.Application.updateRoutes(changes)
    }
  }

  override def onStop(app: Application) {}
}
