package kernel

import com.typesafe.config.ConfigFactory

import akka.cluster.Cluster
import akka.actor.Props
import akka.kernel.Bootable
import akka.actor.ActorSystem

import datastructure.DistributedMatrix

class Backend extends Bootable {
  require(System.getProperty("rm.pid") != null, "Please set pid of this process !")

  import Config._
  val pid = System.getProperty("rm.pid").toInt
  System.setProperty("akka.remote.netty.port", (basePort + pid).toString)

  lazy val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))

  def startup = {
    system.actorOf(Props(new processor.MatrixProcessor(pid, noOfBlocks, blockSize)), name = "matrixmulBackend")
  }

  def shutdown = {
    system.shutdown()
  }

}
