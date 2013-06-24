package dmp

import sbt._
import sbt.Keys._

//Akka Kernel
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions}

//Multi Jvm
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{ MultiJvm }

object DMPBuild extends Build {

  lazy val dmp = Project("dmp", file("."), settings = dmpSettings)configs(MultiJvm)

  val logo = """
              ________   _____  __________
              ___/ __ \  __/  |/  /__/ __ \
              __/ / / /  _/ /|_/ /__/ /_/ /
              _/ /_/ /  _/ /  / / _/ ____/
              /_____/   /_/  /_/  /_/
            \.Distributed Matrix Processor./
  """
  //Init commands loaded before starting scala repl from sbt.
  val initCommands = """
    import kernel.config._
    import kernel.config.routers._
    import scala.concurrent.duration._
    import akka.util.Timeout
    import akka.actor.Props
    import datastructure._ //These are user apis
    implicit val timeout = Timeout(5.seconds)
    lazy val storeRouter = system.actorOf(Props[processor.MatrixStore].withRouter(storeCRC), name = "matrixStoreRouter")
    lazy val facade = system.actorOf(Props[processor.WorkDisseminator].withDispatcher("work-disseminator-dispatcher"), name = "matrixFacade")
    implicit lazy val context = DMPContext(storeRouter, facade)
    println("$logo")
  """.replace("$logo","\"\""+logo+"\"\"")

  def dmpSettings = Defaults.defaultSettings ++ AkkaKernelPlugin.distSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq (
      name                                   :=  "dmp",
      organization                           :=  "org.dmp-project",
      version                                :=  "0.0.1-SNAPSHOT",
      scalaVersion                           :=  "2.10.2",
      scalacOptions                          :=  Seq("-encoding", "UTF-8", "-unchecked", "-optimize", "-deprecation", "-feature"),
      retrieveManaged                        :=  true,
      libraryDependencies                    ++= Dependencies.processorKernel,
      crossPaths                             :=  false,
      fork                                   :=  false,
      javaOptions                            +=  "-Xmx256m", // for testing only
      distJvmOptions in Dist                 :=  "-Xms256M -Xmx1024M",
      outputDirectory in Dist                :=  file("target/dist"),
      initialCommands in console             :=  initCommands,
      compile in MultiJvm                    <<= (compile in MultiJvm) triggeredBy (compile in Test),
      parallelExecution in Test              :=  false,
      unmanagedSourceDirectories in MultiJvm <<= Seq(baseDirectory(_ / "src/distributed-test")).join,
      // multiJvmMarker in MultiJvm             := "ClusterTest",

      // make sure that MultiJvm tests are executed by the default test target
      executeTests in Test                   <<= ((executeTests in Test), (executeTests in MultiJvm)) map {
                                                   case ((_, testResults), (_, multiJvmResults))  =>
                                                     val results = testResults ++ multiJvmResults
                                                     (Tests.overall(results.values), results)
                                                  }
    )
}

object Dependencies {
  import Dependency._

  val processorKernel = Seq( akkaKernel, akkaSlf4j, akkaCluster, log4j12, slf4j) ++ test
}

object Dependency {

  object v {
    val Akka      = "2.2.0-RC1"
    val slf4j     = "1.7.2"
    val log       = "1.0.1"
    val test      = "1.9.1"
    val check     = "1.10.1"
  }

  object s {
    val test = "test"
  }

  val akkaKernel  = "com.typesafe.akka"     %% "akka-kernel"                % v.Akka
  val akkaSlf4j   = "com.typesafe.akka"     %% "akka-slf4j"                 % v.Akka
  val akkaCluster = "com.typesafe.akka"     %% "akka-cluster"               % v.Akka
  val log4j12     = "org.slf4j"              % "slf4j-log4j12"              % v.slf4j
  val slf4j       = "com.typesafe"          %% "scalalogging-slf4j"         % v.log

  val test        = Seq("org.scalatest"     %% "scalatest"                  % v.test  % s.test,
                        "org.scalacheck"    %% "scalacheck"                 % v.check % s.test,
                        "com.typesafe.akka" %% "akka-testkit"               % v.Akka  % s.test,
                        "com.typesafe.akka" %% "akka-multi-node-testkit"    % v.Akka  % s.test)

}
