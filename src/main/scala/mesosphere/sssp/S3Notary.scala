package mesosphere.sssp

import com.amazonaws.auth._
import java.util.Date
import org.jets3t.service.impl.rest.httpclient.RestS3Service
import org.jets3t.service.security.AWSCredentials
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.matching.Regex


class S3Notary(val bucket: String,
               val credentials: AWSCredentialsProvider =
                   new EnvironmentVariableCredentialsProvider(),
               val defaultExpiration: Duration = 10 seconds) {

  var client: RestS3Service = null

  def sign(key: String,
           method: String,
           headers: Map[String, Seq[String]] = Map(),
           lifetime: Duration = defaultExpiration): (String, Long, Date) = {
    if (client == null) reconnect()
    val seconds = (System.currentTimeMillis() + lifetime.toMillis) / 1000L
    val jHeaders: Map[String, AnyRef] =
      S3Notary.normHeaders(headers).mapValues(_.asInstanceOf[AnyRef])
    val url = client
      .createSignedUrl(method, bucket, key, "", jHeaders.asJava, seconds)
    (url, lifetime.toSeconds, new Date(seconds * 1000))
  }

  def reconnect() {
    credentials.refresh()
    val access = credentials.getCredentials.getAWSAccessKeyId
    val secret = credentials.getCredentials.getAWSSecretKey
    client = new RestS3Service(new AWSCredentials(access, secret))
  }
}

object S3Notary {
  /**
   * Filters for and normalizes headers that can be meaningfully passed on to
   * S3 (and influence signing). These include Content-Type, Content-MD5 and
   * the x-amz-... meta-headers peculiar to Amazon.
   *
   * @param headers  HTTP headers in a relatively common format: a map from
   *                 strings to lists of strings.
   * @return         Headers that are "interesting" with multiples entries
   *                 joined by commas.
   */
  def normHeaders(headers: Map[String, Seq[String]]): Map[String, String] = {
    val Special: Regex = "(?i)^(Content-Type|Content-MD5|x-amz-.+)$".r
    headers.collect { case (Special(h), v) => (h -> v.mkString(",")) }
  }
}
