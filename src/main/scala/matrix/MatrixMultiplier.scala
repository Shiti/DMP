package matrix

import scala.math.sqrt
import scala.math.max
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.collection.mutable.Queue

import com.typesafe.config.ConfigFactory
import akka.actor.SupervisorStrategy
import akka.dispatch.Dispatchers

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.pipe

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings

import akka.routing.Broadcast
import akka.routing.Destination
import akka.routing.FromConfig
import akka.routing.NoRouter
import akka.routing.Resizer
import akka.routing.Route
import akka.routing.RouteeProvider
import akka.routing.RouterConfig

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
case class Matrix( name :String, m:Int, n:Int, var data :ArrayBuffer[Int] ) {

  override def toString() : String = {
    s"""\nMatrix $name dimensions $m x $n a row major order traversal\n""" + (for ( w <- data.grouped(n)) yield ( w.mkString(" ") )).mkString("\n")
  }

  def padTo(i : Int) = {
    // helps in resizing
    data = data.padTo(i, 0)
  }

  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorGet( i :Int , j :Int) : Int = {
    if(i < m && j < n) {
      if(i * n + j < data.size) data(i * n + j)
      else 0
    }
      else throw new MatrixIndexOutOfBoundsException(s"$name i:$i and j:$j out of bounds of $m x $n")
  }


  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorSet( i :Int , j :Int, item :Int) : ArrayBuffer[Int] = {
    if(i < m && j < n) {
      if(i * n + j < data.size) {
        data(i * n + j) = item
        data
      }
      else { //Resize should not happen though.
        padTo(i * n + j)
        data(i * n + j) = item
        data
      }
    }
      else throw new MatrixIndexOutOfBoundsException(s"$name i:$i and j:$j out of bounds of $m x $n")
  }

  /*
   * Meant to be inefficient but good looking implementation,'will come back
   * to efficiency using netlib later.
   */
  def x (that : Matrix ) : Matrix = {
    require( n == that.m, s"Matrix multiplication not possible, A( $m x $n) X B(${that.m} x ${that.n})\n")
    var c = Matrix("C", m , that.n, new ArrayBuffer())
    c.padTo(m * that.n)
    println(s"Multiplying $this x $that")
    for ( i <- 0 until m ; j <- 0 until that.n ; k <- 0 until n )
    {
      c.rowMajorSet(i, j, c.rowMajorGet(i,j) + rowMajorGet(i,k) * that.rowMajorGet(k,j))
    }
    c
  }

  def += (that : Matrix) : Matrix = {
    require( n==that.n && m==that.m,s"Matrix addition not possible, Left( $m x $n) + Right(${that.m} x ${that.n})\n" )

     for ( i <- 0 until m ; j <- 0 until that.n )
     {
       rowMajorSet(i, j, rowMajorGet(i,j) + that.rowMajorGet(i,j))
     }
    this
  }

}

/**
 * Distributed Matrix an abstraction representing a matrix ditributed across multiple nodes.
 * NoOfBlocks*blocksize will be new padded size of the matrix. noOfBlocks should be a perfect
 * Square.
 */
case class DistributedMatrix(name :String , row :Int, col : Int, noOfblocks : Int,
                             blockSize : Int, data :ArrayBuffer[Int]) {

  /* Compute padding and make square matrices*/
  val (mPadded, nPadding) = MatrixMultiplierUtility.calculatePadding(row, col, blockSize)

  /** We think of it as padding if it does not exist in the array and yet in range of m x n */
  def rowMajorGet( i :Int , j :Int) : Int = {
    if(i < row && j < col) {
      data(i * col + j)
    }
      else 0
  }

  def getSubMatrix(n : Int): Matrix = {
    require(n < noOfblocks, s"$n should be less than $noOfblocks and is indexed from 0 onwards")
    val blocks1D = sqrt(noOfblocks).toInt
    val i1 = n / blocks1D
    val j1 = n % blocks1D

    val i = i1 * blockSize
    val j = j1 * blockSize
    val k = i + blockSize
    val l = j + blockSize
    println(s"i:$i, $j, $k, $l")
    Matrix(name + n, blockSize, blockSize, getSubMatrix(i, j, k, l))
  }

  private def getSubMatrix(i : Int, j : Int, k : Int, l: Int) : ArrayBuffer[Int] = {
    val array = ArrayBuffer[Int]()
    for ( i1 <- i until k ; j1 <- j until l ){
      array += rowMajorGet(i1,j1)
    }
    array
  }

}

object MatrixMultiplierUtility {

  def calculatePadding( m :Int, n :Int, blockSize : Int) :(Int,Int) = {
    var nPadding = 0
    var mPadding = 0

    if (m != n && max(m,n) == m) (nPadding = m - n) else (mPadding = n - m)

      var x = max(m,n) / blockSize

    if ( max(m, n) != x * blockSize) x = x+1

    (mPadding + x, nPadding + x)
  }

  def zeroPadMatrix(matrix : Matrix, mPadding :Int, nPadding :Int) : Matrix = Matrix(matrix.name, matrix.m + mPadding, matrix.n + nPadding, matrix.data)

}


object MatrixMultiplyFrontend {

  def main(args : Array[String]): Unit  = {

    System.setProperty("akka.remote.netty.port", "0")

    // val config = ConfigFactory.parseString("akka.cluster.roles = [frontend]").
    // withFallback(ConfigFactory.load("application"))

    val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))
    // system.log.info("matrixmul will start when 2 backend members in the cluster.")

    //#registerOnUp
    Cluster(system) registerOnMemberUp {
      system.actorOf(Props[MatrixMultiplyFrontend] , name = "matrixmulFrontend")
    }

  }

}

case class Job (a : Matrix, b : Matrix)

class MatrixMultiplyFrontend extends Actor with ActorLogging {

  val backend = context.actorOf(Props(new MatrixSolverBackend(0, 0, 0))
                                .withRouter(ClusterRouterConfig(CanonsAlgoRouter(nrOfInstances = 8),
                                                                ClusterRouterSettings( totalInstances = 100, routeesPath = "/user/matrixmulBackend", allowLocalRoutees = false)) ) ,
                                name = "matrixmulBackendRouter")

  override def preStart(): Unit = sendJobs()

  def receive = {
    case (n: Int, matrix: Matrix) ⇒
    log.info(s"$sender -> {}! = {}", n, matrix)
  }

  def sendJobs(): Unit = {
     val noOfBlocks = 4
     val blockSize = 2
     val A = DistributedMatrix("A", 4, 4, noOfBlocks, blockSize, ArrayBuffer( 1 to 16 : _*))
     val B = DistributedMatrix("B", 4, 4, noOfBlocks, blockSize, ArrayBuffer( 1 to 16  : _*))
     for (i <- (0 until noOfBlocks)){
       backend ! ((i, Left(A.getSubMatrix(i))))
       backend ! ((i, Up(B.getSubMatrix(i))))
     }
  }
}

object MatrixSolverBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val basePort = 2551
    if (args.nonEmpty) System.setProperty("akka.remote.netty.port", (2551 + args(0).toInt).toString)

    // val config =
    //   (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
    //   else ConfigFactory.empty).withFallback(
    //     ConfigFactory.parseString("akka.cluster.roles = [backend]")).
    //     withFallback(ConfigFactory.load("application"))

    val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))
    system.actorOf(Props(new MatrixSolverBackend(args(0).toInt, 4, 2)), name = "matrixmulBackend")
  }
}

sealed trait Event
case class Left(matrix : Matrix) extends Event
case class Up(matrix: Matrix) extends Event

/*
 * TODO:Assumption pid indexing from 0
 * */
class MatrixSolverBackend(pid: Int, nP : Int, n: Int) extends Actor with ActorLogging {

  lazy val backend = context.actorOf(Props(new MatrixSolverBackend(pid, nP, n))
                                .withRouter(ClusterRouterConfig(CanonsAlgoRouter(nrOfInstances =2),
                                                                ClusterRouterSettings( totalInstances = 100, routeesPath = "/user/matrixmulBackend", allowLocalRoutees = false)) ) ,
                                name = "matrixmulBackendRouter")


  import context.dispatcher
  var rounds = sqrt(nP).toInt

  var matrixA : Option[Matrix]  = None
  var matrixB : Option[Matrix]  = None
  val matrixC : Matrix = Matrix("C", n, n, ArrayBuffer( (0 until (n * n) map (_ * 0) ) : _*))

  val i = pid / rounds
  val j = pid % rounds

  val upPid = (if (i -1 < 0) (rounds - 1) else (i - 1)) * rounds + j
  log.info("My up is:{}", upPid)

  val leftPid = i * rounds + (if(j - 1 < 0) (rounds - 1) else ( j - 1 ) )
  log.info("My left is:{}", leftPid)

  val backLog = new Queue[Event]

  def receive = {

    case Job(a, b) ⇒
      log.info("Got job:{}x{}", a.name, b.name)
      Future(a x b) map { result ⇒ (n, result) } pipeTo sender

    case (_i :Int, Left(matrixL : Matrix)) ⇒

      matrixB match {
        case None ⇒ matrixA match {
                     case None                 ⇒log.info("on Left collected matrix:{}",matrixL)
                                                matrixA = Some(matrixL)
                     case Some(matrix: Matrix) ⇒log.info("on Left collected backlog")
                                                backLog += Left(matrix)
                    }

        case Some(matrix) ⇒ matrixB = None ; matrixA = None
                            log.info("MatrixC :{}", matrixC)
                            matrixC += (matrixL x matrix)
                            if(rounds > 1) {
                               log.info("performing round:{} for:{} on Left", rounds, pid)
                               backend ! ((upPid, Up(matrix)))
                               backend ! ((leftPid, Left(matrixL)))
                               rounds-=1
                            } else log.info("on Left rounds finished. !!!! ")
                            log.info("MatrixC :{}", matrixC)
      }

      log.info("Got message from:{} -->Left",sender)


    case (_i :Int , Up(matrixU : Matrix)) ⇒
      matrixA match {
        case None ⇒ matrixB match {
                       case None                 ⇒ log.info("on Up collected matrix:{}",matrixU)
                                                   matrixB = Some(matrixU)
                       case Some(matrix: Matrix) ⇒ log.info("on Up collected backlog")
                                                   backLog += Up(matrix)
                    }
        case Some(matrix) ⇒ matrixB = None ; matrixA = None
                            log.info("MatrixC :{}", matrixC)
                            matrixC += (matrix x matrixU)
                            if(rounds > 1) {
                               log.info("performing round:{} for:{} on Up", rounds, pid)
                               backend ! ((upPid, Up(matrixU)))
                               backend ! ((leftPid,Left(matrix)))
                               rounds-=1
                            } else log.info("on Up rounds finished. !!!! ")
                            log.info("MatrixC :{}", matrixC)

      }

      log.info("Got message from :{}-->Up",sender)

  }
}

case class CanonsAlgoRouter(nrOfInstances: Int = 0,
                            routees: Iterable[String] = Nil) extends RouterConfig {

  def routerDispatcher: String = Dispatchers.DefaultDispatcherId
  def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy

  def createRoute(routeeProvider: RouteeProvider): Route = {


    routeeProvider.createRoutees(nrOfInstances)


    {

      case (sender, message) ⇒

        val port=""".*akka.\/\/.*:(\d+).*""".r
        val routeesPairs = routeeProvider.routees map {
                                                        x ⇒ x.toString match {
                                                        case port(y:String) ⇒ ((y.toInt-2551)->x)
                                                        case _  ⇒ println("strange!!!"+x.toString); (-1*100->x)
                                                        }
                                                      }

        val routeesMap  = Map(routeesPairs : _*)
        println(routeesMap)
        message match {
          case (pid :Int, Left(matrix: Matrix)) ⇒
            println("Got left routee for " +  pid)
            List(Destination(sender,routeesMap(pid)))
          case (pid :Int, Up(matrix: Matrix)) ⇒
            println("Got routee for " +  pid)
            List(Destination(sender,routeesMap(pid)))
        }
    }
  }

}


/*
*
* Note of future extensions.
*
* Recovery of a lost node by asking or broadcasting ?
* recomputing only the lost data by replaying the event ordering.
* Even ordering to ensure right computation happens and remove unpredictable
* results.
* Make distributed matrix point to a url in DFS/NFS
*
*/
