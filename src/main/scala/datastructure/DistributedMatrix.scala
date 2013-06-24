package datastructure

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.math.sqrt

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.typesafe.scalalogging.slf4j.Logging

import processor._

case class DMPContext(storeRouter : ActorRef, facade :  ActorRef)

/**
 * Distributed Matrix an abstraction representing a matrix distributed across multiple nodes.
 * NoOfBlocks*blocksize will be new padded size of the matrix. noOfBlocks should be a perfect
 * Square.
 */
case class DistributedMatrix(name: String, row: Int, col: Int, noOfblocks: Int, blockSize: Int, data: ArrayBuffer[Int])(implicit context : DMPContext, timeout :Timeout) extends Logging {

  def persist = {
     if(blockSize != 0){
       for (pid <- (0 until noOfblocks)) {
         context.storeRouter ! ((pid, Store(name, getSubMatrixI(pid)) ))
       }
     }
  }



  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  private def rowMajorGet(i: Int, j: Int): Int = {
    if (i < row && j < col) {
      data(i * col + j)
    } else 0
  }

  // This is supposed to be reimplemented for real distributed matrices
   def getSubMatrixI(n: Int): Matrix = {
    require(n < noOfblocks, s"$n should be less than $noOfblocks and is indexed from 0 onwards")
    val blocks1D = sqrt(noOfblocks).toInt
    val i1 = n / blocks1D
    val j1 = n % blocks1D
    val i = i1 * blockSize
    val j = j1 * blockSize
    val k = i + blockSize
    val l = j + blockSize
    logger.trace(s"i:$i, $j, $k, $l")
    Matrix(name, blockSize, blockSize, getSubMatrix(i, j, k, l))
  }

  def getSubMatrix(n: Int)(implicit timeout: Timeout): Matrix = {
    val future = context.storeRouter ? ((n, Get(name)))
    Await.result(future, timeout.duration).asInstanceOf[datastructure.Matrix]
  }

  private def getSubMatrix(i: Int, j: Int, k: Int, l: Int): ArrayBuffer[Int] = {
    val array = ArrayBuffer[Int]()
    for (i1 <- i until k; j1 <- j until l) {
      array += rowMajorGet(i1, j1)
    }
    array
  }

  def x (that :DistributedMatrix) :DistributedMatrix = {
    val future = context.facade ? Multiply(this, that)
    Await.result(future, timeout.duration)
    logger.info("Multiplied {}",name +"x"+ that.name)
    DistributedMatrix(name +"x"+ that.name, noOfblocks)
  }

  def getMatrix()(implicit timeout: Timeout) :Matrix = {
    val rP = sqrt(noOfblocks).toInt
    val nittedContent = (for ( i <- (0  until noOfblocks by rP ) ) yield { (for( j <- ( i until (i+rP)) ) yield(getSubMatrix(j))) reduce (_ ++ _) } ) reduce (_ ::+ _)
    nittedContent
  }

  override def toString() ={
    name
  }
}

object DistributedMatrix {
  def apply (name :String, noOfBlocks: Int)(implicit context : DMPContext, timeout :Timeout) :DistributedMatrix = {
    DistributedMatrix(name, 0, 0, noOfBlocks,0, ArrayBuffer())
  }
}
