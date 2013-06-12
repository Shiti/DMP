package datastructure

import scala.collection.mutable.ArrayBuffer

class MatrixIndexOutOfBoundsException(msg: String) extends RuntimeException(msg)

/**
 * Matrix abstraction represents a matrix in row major order (intended!).
 * There are no constraints on m and n except mxn should be data.length
 *
 * This is supposed to be immutable.
 *
 * Adding more constraints should be easy.
 *
 */
case class Matrix(name: String, m: Int, n: Int, private val data: ArrayBuffer[Int]) {

  require(m * n == data.size, s"Can not initialize matrix with ${data.length} != $m * $n")

  require(name.size != 0, "Name of the matrix can not be left empty!")

  override def toString(): String = {
    s"""\nMatrix $name dimensions $m x $n a row major order traversal\n""" + (for (w <- data.grouped(n)) yield (w.mkString(" "))).mkString("\n")
  }

  /** Not a user API. Used for testing.*/
  private[datastructure] def getData = ArrayBuffer(data :_*)

  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorGet(i: Int, j: Int): Int = {
    if (i < m && j < n) {
      data(i * n + j)
    } else 0
  }

  /*This is not an api so that it remains immutable*/
  private def rowMajorSet(i: Int, j: Int, item: Int) = {
    if (i < m && j < n) {
      data(i * n + j) = item
    } else throw new MatrixIndexOutOfBoundsException(s"$name i:$i and j:$j out of bounds of $m x $n")
  }

  /*
   * Meant to be inefficient but good looking implementation,'will come back
   * to efficiency using netlib later.
   */
  def x(that: Matrix): Matrix = {
    require(n == that.m, s"Matrix multiplication not possible, A( $m x $n) X B(${that.m} x ${that.n})\n")
    var c = Matrix("C", m, that.n, ArrayBuffer((0 until m * that.n).map(x => 0).toSeq: _*))
    for (i <- 0 until m; j <- 0 until that.n; k <- 0 until n) {
      c.rowMajorSet(i, j, c.rowMajorGet(i, j) + rowMajorGet(i, k) * that.rowMajorGet(k, j))
    }
    c
  }

   def + (that: Matrix): Matrix = {
    require(n == that.n && m == that.m, s"Matrix addition not possible, Left( $m x $n) + Right(${that.m} x ${that.n})\n")
    var c = Matrix("C", m, that.n, ArrayBuffer((0 until m * that.n).map(x => 0).toSeq: _*))
    for (i <- 0 until m; j <- 0 until that.n) {
      c.rowMajorSet(i, j, rowMajorGet(i, j) + that.rowMajorGet(i, j))
    }
    c
  }

}
