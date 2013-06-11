package kernel

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster

object MatrixMultiplyFrontend {

  def main(args: Array[String]): Unit = {

    System.setProperty("akka.remote.netty.port", "0")

    // val config = ConfigFactory.parseString("akka.cluster.roles = [frontend]").
    // withFallback(ConfigFactory.load("application"))

    val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))
    // system.log.info("matrixmul will start when 2 backend members in the cluster.")

    //#registerOnUp
    Cluster(system) registerOnMemberUp {
      system.actorOf(Props[processor.WorkDisseminator], name = "matrixmulFrontend")
    }
  }
}
