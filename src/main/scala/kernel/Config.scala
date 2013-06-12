package kernel

import scala.collection.mutable.ArrayBuffer
import scala.math.sqrt

import datastructure.DistributedMatrix

//Set it up here.
object Config {
  val noOfBlocks = 4 // This is also equal to no of processes.
  val blockSize = 5 // This is has to be calculated such that, its 1/2 (max of all dimensions, here it is 10)
  val A = DistributedMatrix("A", 8, 9, noOfBlocks, blockSize, ArrayBuffer(1 to 72: _*)) /*<-- This is the place we specify matrices.*/
  val B = DistributedMatrix("B", 9, 10, noOfBlocks, blockSize, ArrayBuffer(1 to 90: _*)) /*<-- This is the place we specify matrices.*/
  val rounds = sqrt(noOfBlocks).toInt // No of rounds required to converge, So noOfBlocks should be a perfect square.
  val basePort = 2551
}
