package mesosphere.sssp

import java.util.Date
import java.net.URL
import scala.concurrent.duration._
import scala.util.matching.Regex


class S3Notary(val bucket: String,
               val region: Region = Region.getRegion(Regions.US_EAST_1),
               val credentials: AWSCredentialsProvider =
                   new EnvironmentVariableCredentialsProvider(),
               val defaultExpiration: Duration = 10 seconds) {

  var client: S3Client = null

  def sign(key: String,
           method: HttpMethod,
           headers: Map[String, Seq[String]] = Map(),
           lifetime: Duration = defaultExpiration): URL = {
    if (client == null) reconnect()
    val expires = new Date(System.currentTimeMillis() + lifetime.toMillis)
    val request = new GeneratePresignedUrlRequest(bucket, key, method)
    client.generatePresignedUrl(request.withExpiration(expires))
  }

  def reconnect() {
    credentials.refresh()
    client = new S3Client(credentials)
    client.setRegion(region)
  }
}

object S3Notary {
  val regions: Map[String, Region] =
    Regions.values.map(r => (r.getName -> Region.getRegion(r))).toMap +
    ("classic" -> Region.getRegion(Regions.US_EAST_1))

  /* The PUT signing in Amazon's Java SDK does not exactly work out of the box.
  http://stackoverflow.com/questions/10100193/put-file-to-s3-with-presigned-url
   */

  val storableHeaders: Set[String] = Set(
    "Content-Type",
    "Cache-Control",
    "Content-Disposition",
    "Content-Encoding",
    "Content-MD5",
    "Expires",
    "x-amz-acl",
    "x-amz-storage-class"
  )

  val signableHeaders: Set[String] = Set(
    "Content-Type",
    "Content-MD5"
  )

  val metaHeaders: Regex = "^x-amz-".r

  def presignedURLRequestWithMergedHeaders(bucket: String,
                                           key: String,
                                           method: HttpMethod,
                                           headers: Map[String, Seq[String]]):
  GeneratePresignedUrlRequest = {
    val normed = for ((k, v) <- headers) yield (k.toLowerCase -> v)
    val req = new GeneratePresignedUrlRequest(bucket, key, method)

    normed.getOrElse("Content-Type".toLowerCase, Seq()) match {
      case Seq(s, tail@_*) => req.setContentType(s)
      case Seq(          ) => {}
    }

    req
  }
}
