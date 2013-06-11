package processor

import akka.actor.SupervisorStrategy
import akka.dispatch.Dispatchers
import akka.routing.Destination
import akka.routing.Route
import akka.routing.RouteeProvider
import akka.routing.RouterConfig
import datastructure.Matrix

case class CanonsAlgoRouter(nrOfInstances: Int = 0,
  routees: Iterable[String] = Nil) extends RouterConfig {

  def routerDispatcher: String = Dispatchers.DefaultDispatcherId
  def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy

  def createRoute(routeeProvider: RouteeProvider): Route = {

    routeeProvider.createRoutees(nrOfInstances)

    {

      case (sender, message) ⇒

        val port = """.*akka.\/\/.*:(\d+).*""".r
        val routeesPairs = routeeProvider.routees map {
          x ⇒
            x.toString match {
              case port(y: String) ⇒ ((y.toInt - 2551) -> x)
              case _ ⇒ println("strange!!!" + x.toString); (-1 * 100 -> x)
            }
        }

        val routeesMap = Map(routeesPairs: _*)
        println(routeesMap)
        message match {
          case (pid: Int, _x: Int, Left(matrix: Matrix)) ⇒
            println("Got left routee for " + pid)
            List(Destination(sender, routeesMap(pid)))
          case (pid: Int, _x: Int, Up(matrix: Matrix)) ⇒
            println("Got routee for " + pid)
            List(Destination(sender, routeesMap(pid)))
        }
    }
  }

}
