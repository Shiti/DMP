package rm

import sbt._
import sbt.Keys._

object SparkBuild extends Build {

  lazy val rm = Project("rm", file("."), settings = rmSettings)

  def rmSettings = Defaults.defaultSettings ++ Seq (
    name                := "rm",
    organization        := "org.rm-project",
    version             := "0.0.1-SNAPSHOT",
    scalaVersion        := "2.10.1",
    scalacOptions       := Seq("-unchecked", "-optimize", "-deprecation", "-feature"),
    retrieveManaged     := true,
    libraryDependencies ++= Seq(
          "org.scalatest"       %% "scalatest"                      % "1.9.1"   % "test",
          "org.scalacheck"      %% "scalacheck"                     % "1.10.1"  % "test",
          "com.typesafe.akka"   %% "akka-cluster-experimental"      % "2.1.4"
          ),
    parallelExecution   := false,
    fork                := true,
    javaOptions         += "-Xmx256m"
    )
}
