package controllers

import play.api.Play
import play.api.Play.current


object Dist {
  lazy val user = getOr("user", "sssp-dist")
  lazy val pass = getOr("pass", "the-rain-in-spain")

  def getOr(key: String, default: String): String =
    Play.configuration.getConfig("dist")
        .flatMap(_.getString(key)).getOrElse(default)
}
