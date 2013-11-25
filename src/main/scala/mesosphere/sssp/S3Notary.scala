package mesosphere.sssp

import com.amazonaws.HttpMethod
import com.amazonaws.auth._
import com.amazonaws.regions._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import java.util.Date
import java.net.URL
import scala.concurrent.duration._

class S3Notary(val bucket: String,
               val region: Region = Region.getRegion(Regions.US_EAST_1),
               val credentials: AWSCredentialsProvider =
                   new EnvironmentVariableCredentialsProvider(),
               val defaultExpiration: Duration = 10 seconds) {
  var client: AmazonS3Client = null

  def sign(key: String,
           method: HttpMethod,
           lifetime: Duration = defaultExpiration): URL = {
    if (client == null) reconnect()
    val expires = new Date(System.currentTimeMillis() + lifetime.toMillis)
    val request = new GeneratePresignedUrlRequest(bucket, key, method)
    client.generatePresignedUrl(request.withExpiration(expires))
  }

  def reconnect() {
    credentials.refresh()
    client = new AmazonS3Client(credentials)
    client.setRegion(region)
  }
}

object S3Notary {
  val regions: Map[String, Region] =
    Regions.values.map(r => (r.getName -> Region.getRegion(r))).toMap
}
