package datastructure

import org.scalatest.FunSuite
import scala.collection.mutable.ArrayBuffer

class MatrixSuite extends FunSuite {

  val m = 5
  val n = 4
  val name = "A"
  val data = ArrayBuffer(1 to (m * n): _*)
  val mat = Matrix(name, m, n, data)

  test("Requirement fails on initializing Matrix data with incorrect size ") {
    val dataI = ArrayBuffer(1 to 100: _*)
    val thrown = intercept[IllegalArgumentException] {
      Matrix("BadMatrix", m, n, dataI)
    }
    assert(thrown.getMessage === s"requirement failed: Can not initialize matrix with ${dataI.length} != $m * $n")
  }

  test("Matrix can not be initialized with empty name ") {

    val thrown = intercept[IllegalArgumentException] {
      Matrix("", m, n, data)
    }
    assert(thrown.getMessage === s"requirement failed: Name of the matrix can not be left empty!")
  }

  test("Matrix instance is created with correct data") {
    assert(mat.name === name)
    assert(mat.m === m)
    assert(mat.n === n)
    assert(mat.getData === data)
  }

  test("rowMajorGet will return 0 if indices are not in range") {
    val t1 = mat.rowMajorGet(m + 1, n)
    val t2 = mat.rowMajorGet(m, n + 1)
    assert(t1 === 0)
    assert(t2 === 0)
  }

  test("Multiplying two matrices should return expected result"){
    val matA = mat
    val matB = Matrix("B", n, 2, ArrayBuffer( 1 to (n * 2) : _*) )
    val expectedMat = Matrix("C", m, 2, ArrayBuffer(50, 60, 114, 140, 178, 220, 242, 300, 306, 380))
    assert(expectedMat === (matA x matB))
  }

  test("Multiplying matrices of incompatible dimensions leads to exception"){
    val matA = mat
    val matB = Matrix("B", n-1, 2, ArrayBuffer( 1 to ((n-1) * 2) : _*) )
    val thrown = intercept[IllegalArgumentException] {
      matA x matB
    }
    assert(thrown.getMessage === s"requirement failed: Matrix multiplication not possible, A( $m x $n) X B(${matB.m} x ${matB.n})\n")
  }


  test("Adding matrices of incompatible dimensions leads to exception"){
    val matA = mat
    val matB = Matrix("B", n-1, 2, ArrayBuffer( 1 to ((n-1) * 2) : _*) )
    val thrown = intercept[IllegalArgumentException] {
      matA + matB
    }
    assert(thrown.getMessage === s"requirement failed: Matrix addition not possible, Left( $m x $n) + Right(${matB.m} x ${matB.n})\n")
  }

  test("Adding two matrices should return expected result"){
    val matA = mat
    val data2  = ArrayBuffer( (10 until (10 + (m*n))) : _*)
    val matB = Matrix("B", m, n, data2)
    val expectedMat = Matrix("C", m, n, ArrayBuffer(11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35, 37, 39, 41, 43, 45, 47, 49))
    assert(expectedMat === (matA + matB))
  }

  test("Appending matrices row wise should give a appended matrix") {
    val matA = mat
    val data2  = ArrayBuffer( (10 until (10 + (m*n))) : _*)
    val matB = Matrix("B", m, n, data2)
    val expectedMat = Matrix("A++B", m, n + matB.n, ArrayBuffer(1, 2, 3, 4, 10, 11, 12, 13,5, 6, 7, 8, 14, 15, 16, 17,9, 10, 11, 12, 18, 19, 20, 21, 13, 14, 15, 16, 22, 23, 24, 25,17, 18, 19, 20, 26, 27, 28, 29))
    assert(expectedMat === (matA ++ matB))
  }

  test("Appending matrices column wise should give a appended matrix") {
    val matA = mat
    val data2  = ArrayBuffer( (10 until (10 + (m*n))) : _*)
    val matB = Matrix("B", m, n, data2)
    val expectedMat = Matrix("A::B", m + matB.m, n, ArrayBuffer(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29))
    assert(expectedMat === (matA ::+ matB))
  }

}
