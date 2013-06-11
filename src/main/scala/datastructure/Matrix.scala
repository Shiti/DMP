package datastructure

import scala.collection.mutable.ArrayBuffer

class MatrixIndexOutOfBoundsException(msg: String) extends RuntimeException(msg)

/**
 * Matrix abstraction represents a matrix in row major order (intended!).
 * There are no constraints on m and n. If size of data is less than m x n
 * then it is zero padded to achieve the required size. And if size is more
 * than the size of data, then the extra data is simply ignored.
 *
 * Adding above constraints can be easily added by extending Matrix and
 *  putting require inside it.
 *
 */
case class Matrix(name: String, m: Int, n: Int, var data: ArrayBuffer[Int]) {

  override def toString(): String = {
    s"""\nMatrix $name dimensions $m x $n a row major order traversal\n""" + (for (w <- data.grouped(n)) yield (w.mkString(" "))).mkString("\n")
  }

  def padTo(i: Int) = {
    // helps in resizing
    data = data.padTo(i, 0)
  }

  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorGet(i: Int, j: Int): Int = {
    if (i < m && j < n) {
      if (i * n + j < data.size) data(i * n + j)
      else 0
    } else throw new MatrixIndexOutOfBoundsException(s"$name i:$i and j:$j out of bounds of $m x $n")
  }

  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorSet(i: Int, j: Int, item: Int): ArrayBuffer[Int] = {
    if (i < m && j < n) {
      if (i * n + j < data.size) {
        data(i * n + j) = item
        data
      } else { //Resize should not happen though.
        padTo(i * n + j)
        data(i * n + j) = item
        data
      }
    } else throw new MatrixIndexOutOfBoundsException(s"$name i:$i and j:$j out of bounds of $m x $n")
  }

  /*
   * Meant to be inefficient but good looking implementation,'will come back
   * to efficiency using netlib later.
   */
  def x(that: Matrix): Matrix = {
    require(n == that.m, s"Matrix multiplication not possible, A( $m x $n) X B(${that.m} x ${that.n})\n")
    var c = Matrix("C", m, that.n, new ArrayBuffer())
    c.padTo(m * that.n)
    for (i <- 0 until m; j <- 0 until that.n; k <- 0 until n) {
      c.rowMajorSet(i, j, c.rowMajorGet(i, j) + rowMajorGet(i, k) * that.rowMajorGet(k, j))
    }
    c
  }

  def +=(that: Matrix): Matrix = {
    require(n == that.n && m == that.m, s"Matrix addition not possible, Left( $m x $n) + Right(${that.m} x ${that.n})\n")

    for (i <- 0 until m; j <- 0 until that.n) {
      rowMajorSet(i, j, rowMajorGet(i, j) + that.rowMajorGet(i, j))
    }
    this
  }

}
