package datastructure

import scala.collection.mutable.ArrayBuffer
import scala.math.sqrt

import com.typesafe.scalalogging.slf4j.Logging

/**
 * Distributed Matrix an abstraction representing a matrix ditributed across multiple nodes.
 * NoOfBlocks*blocksize will be new padded size of the matrix. noOfBlocks should be a perfect
 * Square.
 */
case class DistributedMatrix(name: String, row: Int, col: Int, noOfblocks: Int,
  blockSize: Int, data: ArrayBuffer[Int]) extends Logging {

  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorGet(i: Int, j: Int): Int = {
    if (i < row && j < col) {
      data(i * col + j)
    } else 0
  }

  def getSubMatrix(n: Int): Matrix = {
    require(n < noOfblocks, s"$n should be less than $noOfblocks and is indexed from 0 onwards")
    val blocks1D = sqrt(noOfblocks).toInt
    val i1 = n / blocks1D
    val j1 = n % blocks1D

    val i = i1 * blockSize
    val j = j1 * blockSize
    val k = i + blockSize
    val l = j + blockSize
    logger.trace(s"i:$i, $j, $k, $l")
    Matrix(name + n, blockSize, blockSize, getSubMatrix(i, j, k, l))
  }

  private def getSubMatrix(i: Int, j: Int, k: Int, l: Int): ArrayBuffer[Int] = {
    val array = ArrayBuffer[Int]()
    for (i1 <- i until k; j1 <- j until l) {
      array += rowMajorGet(i1, j1)
    }
    array
  }

}
