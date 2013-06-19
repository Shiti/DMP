package kernel

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster

import akka.kernel.Bootable

import com.typesafe.config.ConfigFactory

class Frontend extends Bootable {

  import kernel.config.routers.systems._
  import kernel.config.routers.context._

  def startup = {
    system
    Cluster(system) registerOnMemberUp {
      facade
    }
  }

  def shutdown = {
    system.shutdown()
  }

}
