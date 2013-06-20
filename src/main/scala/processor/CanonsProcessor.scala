package processor

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent._
import scala.math.sqrt

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.ActorSelection
import akka.actor.actorRef2Scala
import akka.actor.RootActorPath
import akka.actor.AddressFromURIString
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.pattern.ask
import akka.routing.BroadcastRouter
import akka.routing.FromConfig
import akka.util.Timeout

import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.HeapMetricsSelector

import java.util.concurrent.atomic.AtomicInteger

import datastructure.Matrix

trait Event
sealed trait CannonsEvent extends Event
case class Left(matrix: Matrix) extends CannonsEvent
case class Up(matrix: Matrix) extends CannonsEvent
case class Nothing() extends CannonsEvent
case class Reset() extends Event


/* These are states too .. and that sets the inspiration to make it a state machine. TODO !! */

/*It has a lifecycle, which is Ready -> (receive task) Processing -> (Store Results, Send finished!) -> Reset -> Ready )*/
trait Status
sealed trait ExecutionStatus extends Status
case object Ready extends ExecutionStatus
case object Processing extends ExecutionStatus
case object Finished extends ExecutionStatus

class CanonsProcessor(pid: Int, nP: Int, n: Int) extends Actor with ActorLogging {

  val processorCRS = ClusterRouterSettings(totalInstances = 1000, routeesPath = "/user/matrixProcessor", allowLocalRoutees = false, useRole = Some("backend") )
  val processorCRC = ClusterRouterConfig(SimplePortRouter(pid, nrOfInstances = 100), processorCRS)

  val storeCRS = ClusterRouterSettings(totalInstances = 1000, routeesPath = "/user/matrixStore", allowLocalRoutees = true, useRole = Some("backend"))
  val storeCRC = ClusterRouterConfig(SimplePortRouter(pid, nrOfInstances = 100), storeCRS)

  lazy val processorRouter = context.actorOf(Props(new CanonsProcessor(pid, nP, n)).withRouter(processorCRC), name = "canonsProcessorRouter")

  lazy val storeRouter = context.actorOf(Props[MatrixStore].withRouter(storeCRC), name = "matrixStoreRouter" )

  import context.dispatcher

  val rounds: AtomicInteger = new AtomicInteger(sqrt(nP).toInt)

  var matrixC: Matrix = Matrix("C", n, n, ArrayBuffer((0 until (n * n) map (_ * 0)): _*))

  val i = pid / rounds.get
  val j = pid % rounds.get

  var currentExecutionContext : Option[ActorSelection] = None

  val upPid = (if (i - 1 < 0) (rounds.get - 1) else (i - 1)) * rounds.get + j

  val leftPid = i * rounds.get + (if (j - 1 < 0) (rounds.get - 1) else (j - 1))

  val eventsMap = new TrieMap[Int, CannonsEvent]

  def processEvent(roundId : Int, event : CannonsEvent) = {
    log.info("Got round :{} event {}", roundId, event)
    def processAndSend(matrixL : Matrix, matrixR: Matrix) = {
         eventsMap -= roundId
         matrixC.synchronized {
              matrixC = matrixC + (matrixL x matrixR)
         }
      sendStuff(matrixL, matrixR)
    }

    def sendStuff(matrixL : Matrix, matrixR : Matrix) = {
      rounds.getAndDecrement()
        if (roundId < (sqrt(nP).toInt - 1) ) {
              processorRouter ! ((upPid, roundId + 1, Up(matrixR)))
              processorRouter ! ((leftPid, roundId + 1, Left(matrixL)))
         }
       if (rounds.get == 0) {
           log.info("Rounds finished!!!{}", matrixC)
           storeResultsBlocking(matrixL.name, matrixR.name)
           currentExecutionContext match {
             case None  ⇒
               log.warning(s"!!!unexpected situation!!! executionContext unset while processing in progress")
             case Some(actor) ⇒
               log.info(s"Sending finished to $actor")
               actor !  Finished
               currentExecutionContext = None
           }
           reset()
         }
    }

     (eventsMap getOrElse (roundId, Nothing ), event) match {
       case (Up(matrixR: Matrix), Left(matrixL: Matrix)) ⇒  log.info("Got up and Left !!");processAndSend(matrixL, matrixR)

       case (Left(matrixL: Matrix), Up(matrixR: Matrix)) ⇒  log.info("Got Left and Up !!");processAndSend(matrixL, matrixR)

       case (Nothing, event :CannonsEvent)               ⇒  eventsMap += ((roundId, event));

       case  x                                           ⇒  log.warning(s"Something unexpected happend!! $x")
     }

  }

  def reset() = {
    log.info("Resetting everything!!")
    eventsMap.clear
    matrixC.synchronized {
       matrixC = Matrix("C", n, n, ArrayBuffer((0 until (n * n) map (_ * 0)): _*))
    }
    rounds.set(sqrt(nP).toInt)
  }

  def storeResultsBlocking(nameA: String, nameB: String) = {
    implicit val timeout = Timeout(5.seconds)
    val name = nameA + "x" + nameB
    val future = storeRouter ? (( pid, Store(name , matrixC.copyWithName(name)) ))
    Await.result(future, timeout.duration).asInstanceOf[Stored]
  }

  def receive = {

    case (_i: Int, roundId: Int, event : CannonsEvent ) ⇒ processEvent(roundId, event)

    case (_i: Int, Reset() ) ⇒ reset()

    case (_i: Int, facadePath :String, Ready )  ⇒
       log.info(s"DEBUG: Got message as ready? from $facadePath")
       val status = currentExecutionContext match {
         case None ⇒
            currentExecutionContext = Some(context.actorSelection(facadePath))
            Ready
         case _    ⇒
            Processing
       }
       sender ! status
  }

}
