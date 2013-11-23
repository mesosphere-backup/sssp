name := "sssp"

version := "0.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-java-sdk" % "1.6.2"
)

// resolvers += "Mesosphere Repo" at "http://downloads.mesosphere.io/maven"
// libraryDependencies ++= Seq(
//   "org.apache.mesos" % "mesos" % "0.14.2",
//   "mesosphere" % "mesos-utils" % "0.0.6"
// )

play.Project.playScalaSettings
