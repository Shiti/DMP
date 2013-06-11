package processor

import akka.actor.SupervisorStrategy
import akka.dispatch.Dispatchers
import akka.routing.Destination
import akka.routing.Route
import akka.routing.RouteeProvider
import akka.routing.RouterConfig
import datastructure.Matrix

import com.typesafe.scalalogging.slf4j.Logging

/**
 * Routing is based on port number to identify their process ids Pij
 */
case class SimplePortRouter(nrOfInstances: Int = 0,
  routees: Iterable[String] = Nil) extends RouterConfig with Logging {

  import kernel.Config.basePort

  def routerDispatcher: String = Dispatchers.DefaultDispatcherId
  def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy

  def createRoute(routeeProvider: RouteeProvider): Route = {

    routeeProvider.createRoutees(nrOfInstances)

    {

      case (sender, message) ⇒
        //Extract port number from akka url, used for routing.
        val port = """.*akka.\/\/.*:(\d+).*""".r
        val routeesPairs = routeeProvider.routees map {
          x ⇒
            x.toString match {
              case port(y: String) ⇒ ((y.toInt - basePort) -> x)
              case _ ⇒ throw new IllegalArgumentException(s"Could not extract $port from akka URL ${x.toString}")
            }
        }

        val routeesMap = Map(routeesPairs: _*)
        logger.trace(routeesMap.mkString)
        message match {
          case (pid: Int, _x: Int, Left(matrix: Matrix)) ⇒
            logger.trace("Got left routee for " + pid)
            List(Destination(sender, routeesMap(pid)))
          case (pid: Int, _x: Int, Up(matrix: Matrix)) ⇒
            logger.trace("Got routee for " + pid)
            List(Destination(sender, routeesMap(pid)))
        }
    }
  }

}
