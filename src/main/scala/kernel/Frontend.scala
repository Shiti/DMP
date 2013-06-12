package kernel

import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster

import akka.kernel.Bootable

class Frontend extends Bootable {

  lazy val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))

  def startup = {
    Cluster(system) registerOnMemberUp {
      system.actorOf(Props[processor.WorkDisseminator], name = "matrixmulFrontend")
    }
  }

  def shutdown = {
    system.shutdown()
  }

}
