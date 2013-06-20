package processor

import akka.actor.SupervisorStrategy
import akka.actor.ActorRef

import akka.dispatch.Dispatchers

import akka.routing.Destination
import akka.routing.Route
import akka.routing.RouteeProvider
import akka.routing.RouterConfig

import com.typesafe.scalalogging.slf4j.Logging

import datastructure.Matrix

/**
 * Routing is based on port number to identify their process ids Pij
 */
case class SimplePortRouter(pid :Int, nrOfInstances: Int = 0,
  routees: Iterable[String] = Nil) extends RouterConfig with Logging {

  import kernel.config.basePort

  def routerDispatcher: String = Dispatchers.DefaultDispatcherId
  def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy

  def createRoute(routeeProvider: RouteeProvider): Route = {

    routeeProvider.createRoutees(nrOfInstances)

    {

      case (sender, message) ⇒
        logger.trace(s"Sender: $sender")
        //Extract port number from akka url, used for routing.
        val port = """.*akka.tcp.\/\/.*:(\d+).*""".r
        val routeesPairs = routeeProvider.routees map {
          x ⇒
            x.toString match {
              case port(y: String) ⇒ ((y.toInt - basePort) -> x)
              case _ ⇒ (pid -> x) //This is a strange situation where routee is local
            }
        }

        //TODO: Don't do this everytime. Just first time should suffice!
        val routeesMap = Map(routeesPairs: _*)
        logger.trace(routeesMap.mkString)
        message match {
          case (pid: Int, _x: Int, event :Event) ⇒
            logger.trace("Got routee1 for " + pid)
            List(Destination(sender, routeesMap(pid)))

          case (pid: Int, event :Event) ⇒
            logger.trace("Got routee2 for " + pid)
            List(Destination(sender, routeesMap(pid)))

          case (pid: Int, status :ExecutionStatus) ⇒
            logger.trace("Got routee3 for " + pid)
            List(Destination(sender, routeesMap(pid)))

          case (pid: Int, _, status :ExecutionStatus) ⇒
            logger.trace(s"Got routee4 ")
            List(Destination(sender, routeesMap(pid)))
        }
    }
  }

}
