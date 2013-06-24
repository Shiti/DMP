package kernel

import scala.collection.mutable.ArrayBuffer
import scala.math.sqrt

import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings

import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster

import com.typesafe.config.ConfigFactory

import datastructure.DistributedMatrix
import processor.SimplePortRouter

//Set it up here.
object config {
  val noOfBlocks = 4 // This is also equal to no of processes.
  val blockSize = 5 // This is has to be calculated such that, its 1/2 (max of all dimensions, here it is 10)

  val rounds = sqrt(noOfBlocks).toInt // No of rounds required to converge, So noOfBlocks should be a perfect square.
  val basePort = 2551

  // object exampleMatrix {
  //     lazy val A = DistributedMatrix("A", 8, 9, noOfBlocks, blockSize, ArrayBuffer(1 to 72: _*)) /*<-- This is the place we specify matrices.*/
  //     lazy val B = DistributedMatrix("B", 9, 10, noOfBlocks, blockSize, ArrayBuffer(1 to 90: _*)) /*<-- This is the place we specify matrices.*/
  // }

  object routers {

      val config = ConfigFactory.parseString("akka.cluster.roles = [frontend]").
                                 withFallback(ConfigFactory.load("application"))
       val system = ActorSystem("ClusterSystem", config)
    }


    val storeCRS = ClusterRouterSettings(totalInstances = 1000, routeesPath = "/user/matrixStore", allowLocalRoutees = true, useRole = Some("backend") )
    val storeCRC = ClusterRouterConfig(SimplePortRouter(0, nrOfInstances = 100), storeCRS)

    object context {
      import kernel.config.routers._
        lazy val storeRouter = system.actorOf(Props[processor.MatrixStore].withRouter(storeCRC), name = "matrixStoreRouter")
        lazy val facade = system.actorOf(Props[processor.WorkDisseminator].withDispatcher("work-disseminator-dispatcher"), name = "matrixFacade")
      }
}
