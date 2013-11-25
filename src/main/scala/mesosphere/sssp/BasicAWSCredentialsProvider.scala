package mesosphere.sssp

import com.amazonaws.auth._


class BasicAWSCredentialsProvider(access: String, secret: String)
    extends AWSCredentialsProvider {
  def refresh() {}
  def getCredentials: AWSCredentials = new BasicAWSCredentials(access, secret)
}
