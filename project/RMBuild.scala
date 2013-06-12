package rm

import sbt._
import sbt.Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions}


object SparkBuild extends Build {

  lazy val rm = Project("rm", file("."), settings = rmSettings)

  def rmSettings = Defaults.defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq (
    name                    := "rm",
    organization            := "org.rm-project",
    version                 := "0.0.1-SNAPSHOT",
    scalaVersion            := "2.10.2",
    scalacOptions           := Seq("-encoding", "UTF-8", "-unchecked", "-optimize", "-deprecation", "-feature"),
    retrieveManaged         := true,
    libraryDependencies     ++= Dependencies.processorKernel,
    crossPaths              := false,
    fork                    := true,
    javaOptions             += "-Xmx256m", // for testing only
    distJvmOptions in Dist  := "-Xms256M -Xmx1024M",
    outputDirectory in Dist := file("target/dist")
    )

}



object Dependencies {
  import Dependency._

  val processorKernel = Seq( akkaKernel, akkaSlf4j, akkaCluster, log4j12, slf4j) ++ test
}

object Dependency {

  object v {
    val Akka      = "2.1.4"
    val slf4j     = "1.7.2"
    val log       = "1.0.1" /* <- this has to be removed with http://blog.tmorris.net/posts/the-writer-monad-using-scala-example */
    val test      = "1.9.1"
    val check     = "1.10.1"
  }

  object s {
    val test = "test"
  }

  val akkaKernel  = "com.typesafe.akka"  %% "akka-kernel"                % v.Akka
  val akkaSlf4j   = "com.typesafe.akka"  %% "akka-slf4j"                 % v.Akka
  val akkaCluster = "com.typesafe.akka"  %% "akka-cluster-experimental"  % v.Akka
  val log4j12     = "org.slf4j"           % "slf4j-log4j12"              % v.slf4j
  val slf4j       = "com.typesafe"       %% "scalalogging-slf4j"         % v.log
  val test        = Seq("org.scalatest"  %% "scalatest"                  % v.test  % s.test,
                        "org.scalacheck" %% "scalacheck"                 % v.check % s.test)

}
