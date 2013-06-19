package processor

import scala.collection.concurrent.TrieMap

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala

import datastructure.Matrix

sealed trait StoreEvent extends Event
case class Store(name: String, matrix: Matrix) extends StoreEvent
case class Get(name: String) extends StoreEvent
case class Remove(name: String) extends StoreEvent
case object NotFound extends StoreEvent
case class Stored() extends StoreEvent

class MatrixStore extends Actor with ActorLogging {

  import context.dispatcher

  val refsMap = new TrieMap[String, Matrix]

  def receive = {
    case (_i: Int, Get(name: String)) ⇒
          sender ! refsMap.getOrElse(name, NotFound)

    case (_i: Int, Store(name: String, matrix: Matrix)) ⇒
          log.info("stored matrix {} with name {}", matrix, name)
          refsMap += ((name, matrix))
          sender ! Stored()

    case (_i: Int, Remove(name: String)) ⇒ refsMap -= name

    case (_i: Int, Reset ) ⇒ refsMap.clear
  }
}
