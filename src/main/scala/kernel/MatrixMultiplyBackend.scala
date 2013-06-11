package kernel

import com.typesafe.config.ConfigFactory
import akka.actor.SupervisorStrategy
import akka.dispatch.Dispatchers

import akka.actor.ActorSystem

import scala.collection.mutable.ArrayBuffer
import datastructure.DistributedMatrix
import akka.cluster.Cluster
import scala.math.sqrt
import akka.actor.Props

//Set it up here.
object Config {
  val noOfBlocks = 4 // This is also equal to no of processes.
  val blockSize = 5 // This is has to be calculated such that, its 1/2 (max of all dimensions, here it is 10)
  val A = DistributedMatrix("A", 8, 9, noOfBlocks, blockSize, ArrayBuffer(1 to 72: _*)) /*<-- This is the place we specify matrices.*/
  val B = DistributedMatrix("B", 9, 10, noOfBlocks, blockSize, ArrayBuffer(1 to 90: _*)) /*<-- This is the place we specify matrices.*/
  val rounds = sqrt(noOfBlocks).toInt // No of rounds required to converge, So noOfBlocks should be a perfect square.
}

object MatrixMultiplyBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val basePort = 2551
    if (args.nonEmpty) System.setProperty("akka.remote.netty.port", (2551 + args(0).toInt).toString)

    val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))
    import Config._
    system.actorOf(Props(new processor.MatrixMultiplyBackend(args(0).toInt, noOfBlocks, blockSize)), name = "matrixmulBackend")
  }
}
