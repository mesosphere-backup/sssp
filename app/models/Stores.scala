package models

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import scala.concurrent.stm._

import play.Logger
import play.api.libs.json._

import mesosphere.sssp._


object Stores {
  val log = Logger.of("stores")
  val routes = new Routes()

  def updateRoutes(map: Map[String, Change], clearing: Boolean = false) = {
    // We break in to the stores implementation here, to transactionally update
    // the routes.
    atomic { implicit txn =>
      val inner: TMap[Seq[String], S3Notary] = routes.routes
      if (clearing) {
        log.info("*cleared*")
        inner.clear()
      }
      for ((s, change) <- map) {
        val path = s.split('/').filterNot(_.isEmpty).toSeq
        log.info(s"$s -> ${change.summary}")
        change match {
          case Remove() => inner -= path
          case AddS3(S3(bucket, access, secret)) => {
            val creds = new BasicAWSCredentials(access, secret)
            val provider = new StaticCredentialsProvider(creds)
            inner += (path -> new S3Notary(bucket, provider))
          }
        }
      }
    }
  }

  def routesAsChanges(passwordProtect: Boolean = true): Map[String, Change] =
    for ((path, notary) <- routes.toMap) yield {
      val creds = notary.credentials.getCredentials()
      val s3 = if (passwordProtect) {
        S3(notary.bucket, creds.getAWSAccessKeyId.map(_ => '•'),
          creds.getAWSSecretKey.map(_ => '•'))
      } else {
        S3(notary.bucket, creds.getAWSAccessKeyId, creds.getAWSSecretKey)
      }
      (("/" + path.mkString("/")) -> AddS3(s3))
    }

  def routesAsJson(passwordProtect: Boolean = true): JsValue = {
    Json.toJson(routesAsChanges(passwordProtect).mapValues(Json.toJson(_)))
  }
}
