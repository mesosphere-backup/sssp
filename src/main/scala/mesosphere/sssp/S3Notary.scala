package mesosphere.sssp

import com.amazonaws.HttpMethod
import com.amazonaws.auth._
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions._
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import java.util.Date
import java.net.URL
import scala.concurrent.duration.Duration


class S3Notary(val bucket: String,
               val region: Region = Region.getRegion(DEFAULT_REGION),
               val credentials: AWSCredentialsProvider =
                   new EnvironmentVariableCredentialsProvider()) {
  var client: AmazonS3Client = null

  def sign(key: String, method: HttpMethod, lifetime: Duration): URL = {
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
