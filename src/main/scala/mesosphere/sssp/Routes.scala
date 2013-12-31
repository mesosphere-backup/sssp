package mesosphere.sssp

import scala.collection.mutable
import scala.concurrent.stm._


class Routes extends mutable.Map[Seq[String], S3Notary] {
  val routes: TMap[Seq[String], S3Notary] = TMap()

  def deepestHandler(path: Seq[String]): Option[(Seq[String], S3Notary)] =
    path.inits.map(pre => get(pre).map((pre, _))).toSeq.flatten.lastOption

  def -=(k: Seq[String]): Routes.this.type = {
    routes.single -= k
    this
  }

  def +=(kv: (Seq[String], S3Notary)): Routes.this.type = {
    routes.single += kv
    this
  }

  def get(k: Seq[String]): Option[S3Notary] = routes.single.get(k)

  def iterator: Iterator[(Seq[String], S3Notary)] = routes.snapshot.iterator

  override def empty: Routes = new Routes()

  override def size: Int = routes.single.size
}
