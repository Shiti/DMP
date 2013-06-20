package implicits

import akka.actor.{ActorSystem, ExtendedActorSystem}
import akka.remote.RemoteActorRefProvider

object ImplicitConversions {

   implicit class FurtherExtendedActorSystem(system: ActorSystem) {
       val provider = system.asInstanceOf[ExtendedActorSystem].provider
       val address = provider.getDefaultAddress
       val boundPort = address.port.get
       val host = address.host.get

       def getRemotePath() = address
       def /(path :String) = address+path
    }

}
