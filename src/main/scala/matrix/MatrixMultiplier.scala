package matrix

import scala.math.sqrt
import scala.math.max
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.collection.concurrent.TrieMap
import scala.collection.mutable.Queue
import scala.annotation.tailrec

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


/**
 * Instructions to run.
 *
 *
 * First configure application.conf to list seed nodes and your host.
 * Then start Backend nodes first and they should be equal to No of blocks mentioned in Config.
 * for starting backend nodes.
 * sbt "run-main matrix.MatrixSolverBackend id"
 * The id above is process if starting from 0. 1,2,3 etc.. should be continuous.
 * Then start frontend in similar way to send jobs.
 * sbt "run-main matrix.MatrixMultiplyFrontend"
 */



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

//Set it up here.
object Config {
     val noOfBlocks = 4 // This is also equal to no of processes.
     val blockSize = 5 // This is has to be calculated such that, its 1/2 (max of all dimensions, here it is 10)
     val A = DistributedMatrix("A", 8, 9, noOfBlocks, blockSize, ArrayBuffer( 1 to 72 : _*)) /*<-- This is the place we specify matrices.*/
     val B = DistributedMatrix("B", 9, 10, noOfBlocks, blockSize, ArrayBuffer( 1 to 90  : _*)) /*<-- This is the place we specify matrices.*/
     val rounds = sqrt(noOfBlocks).toInt // No of rounds required to converge, So noOfBlocks should be a perfect square.
}

object Utility {

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

//This is no more used.
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

  import Config._

  def sendJobs(): Unit = {

    @tailrec def downPid(pid : Int, recurse :Int) :Int = {
       if(recurse == 0)
           pid
       else {
           val (i ,j) = pidToijPair(pid)
          downPid((if (i +1 >=rounds) (0) else (i + 1)) * rounds + j, recurse - 1) /*TODO:I should use modulus! here, but cant risk breaking it*/
       }
     }

     @tailrec def rightPid(pid :Int, recurse :Int) :Int = {
          if(recurse == 0){
            println(s"pid:::$pid")
           pid
          }
          else {
               val (i ,j) = pidToijPair(pid)
               println(s"i:$i,j:$j:recurse:$recurse")
               rightPid(i * rounds + (if(j + 1 >= rounds) (0) else ( j + 1 ) ), recurse -1) /*TODO: This too...*/
          }
      }

     def pidToijPair(pid :Int): (Int, Int) = ((pid / rounds) , (pid % rounds))

     for (pid <- (0 until noOfBlocks)){
       val (i ,j) = pidToijPair(pid)
       val rPid = rightPid(pid, i)
       val dPid = downPid(pid, j)
       backend ! ((pid, 0,  Left(A.getSubMatrix(rPid))))
       backend ! ((pid, 0,  Up(B.getSubMatrix(dPid))))
     }
  }
}

object MatrixSolverBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val basePort = 2551
    if (args.nonEmpty) System.setProperty("akka.remote.netty.port", (2551 + args(0).toInt).toString)

    val system = ActorSystem("ClusterSystem", ConfigFactory.load("application"))
    import Config._
    system.actorOf(Props(new MatrixSolverBackend(args(0).toInt, noOfBlocks, blockSize)), name = "matrixmulBackend")
  }
}

sealed trait Event
case class Left(matrix : Matrix) extends Event
case class Up(matrix: Matrix) extends Event
case class Nothing() extends Event

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

  log.info("Started with i:{}, j:{} and pid:{}", i, j, pid)

  val upPid = (if (i -1 < 0) (rounds - 1) else (i - 1)) * rounds + j
  log.info("My up is:{}", upPid)

  val leftPid = i * rounds + (if(j - 1 < 0) (rounds - 1) else ( j - 1 ) )
  log.info("My left is:{}", leftPid)

  val eventsMap = new TrieMap[Int, Event]

  def receive = {

    case Job(a, b) ⇒
      log.info("Got job:{}x{}", a.name, b.name)
      Future(a x b) map { result ⇒ (n, result) } pipeTo sender

    case (_i :Int, roundId :Int, Left(matrixL : Matrix)) ⇒
      val event = eventsMap getOrElse (roundId, Left(matrixL))
      event match {
        case Up(matrix : Matrix) ⇒ /*Process and remove*/
                                   eventsMap -= roundId
                                   log.info("MatrixC :{}", matrixC)
                                   matrixC += (matrixL x matrix)
                                   if(rounds > 1) {
                                      log.info("performing round:{} for:{} on Left", rounds, pid)
                                      backend ! ((upPid, roundId + 1, Up(matrix)))
                                      backend ! ((leftPid, roundId + 1, Left(matrixL)))
                                      rounds -= 1
                                   } else log.info("on Left rounds finished. !!!! ")
                                   log.info("MatrixC :{}", matrixC)

        case Left(_) ⇒  /* Append to map*/
                        log.info("on Left collected matrix:{}",matrixL)
                        eventsMap += ((roundId, Left(matrixL)))

        case Nothing() ⇒ /*Not possible*/
      }

      log.info("Got message from:{} -->Left",sender)


    case (_i :Int , roundId :Int, Up(matrixU : Matrix)) ⇒
        val event = eventsMap getOrElse (roundId, Up(matrixU))
          event match {
            case Up(_) ⇒ /* Append to map*/
                        log.info("on Up collected matrix:{}", matrixU)
                        eventsMap += ((roundId, Up(matrixU)))


            case Left(matrix : Matrix) ⇒  /*Process and remove*/
                            eventsMap -= roundId
                            log.info("MatrixC :{}", matrixC)
                            matrixC += (matrix x matrixU)
                            if(rounds > 1) {
                               log.info("performing round:{} for:{} on Up", rounds, pid)
                               backend ! ((upPid, roundId + 1, Up(matrixU)))
                               backend ! ((leftPid, roundId + 1, Left(matrix)))
                               rounds -= 1
                            } else log.info("on Left rounds finished. !!!! ")
                            log.info("MatrixC :{}", matrixC)

            case Nothing() ⇒ /*Not possible*/
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
          case (pid :Int, _x: Int, Left(matrix: Matrix)) ⇒
            println("Got left routee for " +  pid)
            List(Destination(sender,routeesMap(pid)))
          case (pid :Int, _x: Int, Up(matrix: Matrix)) ⇒
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
