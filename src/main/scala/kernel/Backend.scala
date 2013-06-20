package kernel

import com.typesafe.config.ConfigFactory

import akka.cluster.Cluster
import akka.actor.Props
import akka.kernel.Bootable
import akka.actor.ActorSystem

import datastructure.DistributedMatrix

class Backend extends Bootable {
  require(System.getProperty("rm.pid") != "" && System.getProperty("rm.pid") != null , "Please set pid of this process !")

  import config._
  val pid = System.getProperty("rm.pid").toInt

    val frontendConfig = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${basePort + pid}").withFallback(ConfigFactory.parseString("akka.cluster.roles = [backend]")).withFallback(ConfigFactory.load("application"))

    val system = ActorSystem("ClusterSystem", frontendConfig)


  def startup = {
    System.setProperty("akka.remote.netty.port", (basePort + pid).toString)
    system.actorOf(Props(new processor.CanonsProcessor(pid, noOfBlocks, blockSize)), name = "matrixProcessor")
    system.actorOf(Props[processor.MatrixStore], name = "matrixStore")
    System.setProperty("akka.remote.netty.port", (0).toString)
  }

  def shutdown = {
    system.shutdown()
  }

}
