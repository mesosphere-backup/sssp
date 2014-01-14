name := "sssp"

scalaVersion := "2.10.3"

version := "0.0-SNAPSHOT"

resolvers ++= Seq(
  "mesosphere"  at "http://downloads.mesosphere.io/maven",
  "jets3t"      at "http://www.jets3t.org/maven2"
)

libraryDependencies ++= Seq(
  "org.joda"            % "joda-convert"  % "1.5",
  "com.amazonaws"       % "aws-java-sdk"  % "1.6.2",
  "net.java.dev.jets3t" % "jets3t"        % "0.9.0",
  "org.apache.mesos"    % "mesos"         % "0.14.2",
  "mesosphere"          % "mesos-utils"   % "0.0.6",
  "org.scalatest" 		%% "scalatest" 	  % "2.0.M5b" % "test"
)

unmanagedSourceDirectories in Compile <+= baseDirectory / "src/main/scala"

unmanagedSourceDirectories in Test <+= baseDirectory / "src/main/scala"

play.Project.playScalaSettings
