package processor

import scala.annotation.tailrec

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import datastructure.Matrix
import kernel.Config.A
import kernel.Config.B
import kernel.Config.noOfBlocks
import kernel.Config.rounds

class MatrixMultiplyFrontend extends Actor with ActorLogging {

  val backend = context.actorOf(Props(new MatrixMultiplyBackend(0, 0, 0))
    .withRouter(ClusterRouterConfig(CanonsAlgoRouter(nrOfInstances = 8),
      ClusterRouterSettings(totalInstances = 100, routeesPath = "/user/matrixmulBackend", allowLocalRoutees = false))),
    name = "matrixmulBackendRouter")

  override def preStart(): Unit = sendJobs()

  def receive = {
    case (n: Int, matrix: Matrix) ⇒
      log.info(s"$sender -> {}! = {}", n, matrix)
  }

  import kernel.Config._

  def sendJobs(): Unit = {

    @tailrec def downPid(pid: Int, recurse: Int): Int = {
      if (recurse == 0)
        pid
      else {
        val (i, j) = pidToijPair(pid)
        downPid((if (i + 1 >= rounds) (0) else (i + 1)) * rounds + j, recurse - 1) /*TODO:I should use modulus! here, but cant risk breaking it*/
      }
    }

    @tailrec def rightPid(pid: Int, recurse: Int): Int = {
      if (recurse == 0) {
        println(s"pid:::$pid")
        pid
      } else {
        val (i, j) = pidToijPair(pid)
        println(s"i:$i,j:$j:recurse:$recurse")
        rightPid(i * rounds + (if (j + 1 >= rounds) (0) else (j + 1)), recurse - 1) /*TODO: This too...*/
      }
    }

    def pidToijPair(pid: Int): (Int, Int) = ((pid / rounds), (pid % rounds))

    for (pid <- (0 until noOfBlocks)) {
      val (i, j) = pidToijPair(pid)
      val rPid = rightPid(pid, i)
      val dPid = downPid(pid, j)
      backend ! ((pid, 0, Left(A.getSubMatrix(rPid))))
      backend ! ((pid, 0, Up(B.getSubMatrix(dPid))))
    }
  }
}
