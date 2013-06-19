package kernel

import com.typesafe.config.ConfigFactory

import akka.cluster.Cluster
import akka.actor.Props
import akka.kernel.Bootable
import akka.actor.ActorSystem

import datastructure.DistributedMatrix

class Backend extends Bootable {
  require(System.getProperty("rm.pid") != null, "Please set pid of this process !")

  import config._
  val pid = System.getProperty("rm.pid").toInt
  // System.setProperty("akka.remote.netty.port", (basePort + pid).toString)

   import config.routers.systems._

  def startup = {
    System.setProperty("akka.remote.netty.port", (basePort + pid).toString)
    system.actorOf(Props(new processor.MatrixProcessor(pid, noOfBlocks, blockSize)), name = "matrixProcessor")
    system.actorOf(Props[processor.MatrixStore], name = "matrixStore")
    System.setProperty("akka.remote.netty.port", (0).toString)
    // remoteSystem
  }

  def shutdown = {
    system.shutdown()
  }

}
