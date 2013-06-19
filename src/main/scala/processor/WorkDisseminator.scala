package processor

import scala.annotation.tailrec

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings

import scala.concurrent.Await
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent._

import java.util.concurrent.CountDownLatch

import datastructure.Matrix
import datastructure.DistributedMatrix
import implicits.ImplicitConversions.FurtherExtendedActorSystem

case object Started
case class Multiply(nameA: DistributedMatrix, nameB: DistributedMatrix)


class WorkDisseminator extends Actor with ActorLogging {

  import kernel.config.routers._
  import kernel.config.routers.context._

  implicit val timeout = Timeout(5.seconds)

  import kernel.config._

  var countDownLatch = new CountDownLatch(noOfBlocks)

  import context.dispatcher

  def postStart(): Unit = {
         storeRouter
         processorRouter
      // if(areWorkersReady(noOfBlocks - 1 )) {
     //      //sendJobs()
     //      log.info("Waiting for results")
     //      awaitResults
     //      log.info("Got results")
     //      getResults()
     //  }else {
     //    log.info("Workers not ready yet.!!")
     // }
  }

  def reset() = {
    countDownLatch  = new CountDownLatch(noOfBlocks)
  }

  def getResults() = {
   for (pid <- (0 until noOfBlocks)) {
       val future = storeRouter ? ((pid, Get("AxB")))
       val result = Await.result(future, timeout.duration).asInstanceOf[datastructure.Matrix]
       log.info(s"$result")
    }
  }

  def awaitResults() = {
    log.info("Waiting for results")
    countDownLatch.await()
    log.info("Got results")
  }

  self ! Started

  def receive = {

    case Started ⇒
      // log.info("got started!!")
      // val f = future {
      //   println("Executing in future!!")
      //   postStart
      // }

      // f onSuccess {
      //   case _ => println("Success!!")
      // }

      // f onFailure {
      //   case t => t.printStackTrace ; println("An error has occured: " + t)
      // }

    case (n: Int, matrix: Matrix) ⇒
      log.info(s"$sender -> {}! = {}", n, matrix)

    case Finished  ⇒
      log.info(s"Got finished from $sender")
      countDownLatch.countDown()

    case Reset   ⇒ reset()

    case Multiply(a : DistributedMatrix, b: DistributedMatrix) ⇒
      val senderClosed = sender
      val f = future {
        if(areWorkersReady(noOfBlocks - 1 )) {
          sendJobs(a, b)
          awaitResults
        } else {
          log.info("Workers not ready yet!! Try again later.")
        }
      }

      f onComplete {
        case _ => log.info("Finished!");senderClosed ! Finished
      }
  }


  @tailrec final def areWorkersReady(n: Int): Boolean = {
    if(n < 0) true
    else {
      log.info(s"checking readiness of $n")
      val status = processorRouter ? ((n , FurtherExtendedActorSystem(context.system)/"/user/matrixFacade", Ready))
      val result  = Await.result(status, timeout.duration).asInstanceOf[ExecutionStatus]
      log.info(s"got:$result")
      val idle = result match {
        case Ready ⇒ true
        case _ ⇒ false
      }
      idle && areWorkersReady(n -1)
    }
  }


  def sendJobs(A: DistributedMatrix, B: DistributedMatrix): Unit = {

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
        pid
      } else {
        val (i, j) = pidToijPair(pid)
        rightPid(i * rounds + (if (j + 1 >= rounds) (0) else (j + 1)), recurse - 1) /*TODO: This too...*/
      }
    }

    def pidToijPair(pid: Int): (Int, Int) = ((pid / rounds), (pid % rounds))

    for (pid <- (0 until noOfBlocks)) {
      val (i, j) = pidToijPair(pid)
      val rPid = rightPid(pid, i)
      val dPid = downPid(pid, j)

      processorRouter ! ((pid, 0, Left(A.getSubMatrix(rPid))))
      processorRouter ! ((pid, 0, Up(B.getSubMatrix(dPid))))
    }
  }
}
