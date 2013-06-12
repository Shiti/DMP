package processor

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.math.sqrt

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import datastructure.Matrix

sealed trait Event
case class Left(matrix: Matrix) extends Event
case class Up(matrix: Matrix) extends Event
case class Nothing() extends Event


class MatrixProcessor(pid: Int, nP: Int, n: Int) extends Actor with ActorLogging {

  lazy val router = context.actorOf(Props(new MatrixProcessor(pid, nP, n))
    .withRouter(ClusterRouterConfig(SimplePortRouter(nrOfInstances = 2),
      ClusterRouterSettings(totalInstances = 100, routeesPath = "/user/matrixmulBackend", allowLocalRoutees = false))),
    name = "matrixmulBackendRouter")

  import context.dispatcher

  var rounds = sqrt(nP).toInt

  var matrixA: Option[Matrix] = None
  var matrixB: Option[Matrix] = None
  var matrixC: Matrix = Matrix("C", n, n, ArrayBuffer((0 until (n * n) map (_ * 0)): _*))

  val i = pid / rounds
  val j = pid % rounds

  log.info("Started with i:{}, j:{} and pid:{}", i, j, pid)

  val upPid = (if (i - 1 < 0) (rounds - 1) else (i - 1)) * rounds + j
  log.info("My up is:{}", upPid)

  val leftPid = i * rounds + (if (j - 1 < 0) (rounds - 1) else (j - 1))
  log.info("My left is:{}", leftPid)

  val eventsMap = new TrieMap[Int, Event]

  def receive = {

    case (_i: Int, roundId: Int, Left(matrixL: Matrix)) ⇒
      log.warning("Received:{} -> roundId{} ->Left", _i, roundId)
      val event = eventsMap getOrElse (roundId, Left(matrixL))
      event match {
        case Up(matrix: Matrix) ⇒ /*Process and remove*/
          eventsMap -= roundId
          log.info("MatrixC :{}", matrixC)
          matrixC = matrixC + (matrixL x matrix)
          if (rounds > 1) {
            log.info("performing round:{} for:{} on Left", rounds, pid)
            router ! ((upPid, roundId + 1, Up(matrix)))
            router ! ((leftPid, roundId + 1, Left(matrixL)))
            rounds -= 1
          } else log.info("on Left rounds finished. !!!! ")
          log.info("MatrixC :{}", matrixC)

        case Left(_) ⇒ /* Append to map*/
          log.info("on Left collected matrix:{}", matrixL)
          eventsMap += ((roundId, Left(matrixL)))

        case Nothing() ⇒ /*Not possible*/
      }

      log.info("Got message from:{} -->Left", sender)

    case (_i: Int, roundId: Int, Up(matrixU: Matrix)) ⇒
      log.warning("Received:{} -> roundId{} ->Up", _i, roundId)
      val event = eventsMap getOrElse (roundId, Up(matrixU))
      event match {
        case Up(_) ⇒ /* Append to map*/
          log.info("on Up collected matrix:{}", matrixU)
          eventsMap += ((roundId, Up(matrixU)))

        case Left(matrix: Matrix) ⇒ /*Process and remove*/
          eventsMap -= roundId
          log.info("MatrixC :{}", matrixC)
          matrixC = matrixC + (matrix x matrixU)
          if (rounds > 1) {
            log.info("performing round:{} for:{} on Up", rounds, pid)
            router ! ((upPid, roundId + 1, Up(matrixU)))
            router ! ((leftPid, roundId + 1, Left(matrix)))
            rounds -= 1
          } else log.info("on Left rounds finished. !!!! ")
          log.info("MatrixC :{}", matrixC)

        case Nothing() ⇒ /*Not possible*/
      }
      log.info("Got message from :{}-->Up", sender)
  }

}
