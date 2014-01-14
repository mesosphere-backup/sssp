package mesosphere.sssp.test

import mesosphere.sssp.{Routes, S3Notary}


class RoutesSpec extends Spec {

  case class Fixture() { val routes = new Routes() }

  "RoutesSpec" should "select the deepest (most specific) route for a path" in {
    val f = Fixture()
    val notaryA = new S3Notary("bucketA")
    val notaryB = new S3Notary("bucketB")
    val notaryC = new S3Notary("bucketC")

    val pathA = Seq("a", "b")
    val pathB = Seq("a", "b", "c")
    val pathC = Seq("a", "a", "c")

    f.routes += pathA -> notaryA
    f.routes += pathB -> notaryB
    f.routes += pathC -> notaryC

    val query1 = Seq("a", "b", "x")
    f.routes.deepestHandler(query1) should equal (Some(pathA -> notaryA))

    val query2 = Seq("a", "b", "c", "x")
    f.routes.deepestHandler(query2) should equal (Some(pathB -> notaryB))

    val query3 = Seq("a", "a", "c")
    f.routes.deepestHandler(query3) should equal (Some(pathC -> notaryC))
  }
	
}