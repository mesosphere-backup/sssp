package util

import java.net.InetAddress


case class Listener(hostname: String, ip: String, port: Int)

object Listener {
  /**
   * Try to guess which host and port we're listening on. Needed for
   * self-download of SSSP.
   * @return Listener for the present application.
   */
  def guess(): Listener = {
    val inet = InetAddress.getLocalHost()
    val port = Option(System.getProperty("http.port")).getOrElse("9000").toInt
    val ip   = Option(System.getProperty("http.address"))
      .getOrElse(InetAddress.getLocalHost().getHostAddress())
    Listener(inet.getHostName, ip, port)
  }
}
