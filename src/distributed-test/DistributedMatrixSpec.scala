package distributed

import akka.actor.ActorSystem
import akka.actor.AddressFromURIString
import akka.actor.Props
import akka.cluster.Cluster
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor.ActorRef
import com.typesafe.config.ConfigFactory
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings

import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import org.scalatest.FunSuite
import processor._
import datastructure._

import scala.collection.mutable.MutableList
// four processor spec

object TestConfig{
    val noOfBlocks = 4 // This is also equal to no of processes.
    val blockSize = 5 // This is has to be calculated such that, its 1/2 (max of all dimensions, here it is 10)

    val rounds = 2 // No of rounds required to converge, So noOfBlocks should be a perfect square.
    val basePort = 2551
    implicit val timeout = Timeout(5.seconds)
    def sleep = Thread.sleep(40000)
    def generateConfig(pid:Int, role:String) = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${basePort + pid}").
                                               withFallback(ConfigFactory.parseString(s"akka.cluster.roles = [$role]")).withFallback(ConfigFactory.load("application")).
                                               withFallback(ConfigFactory.parseString(s"""akka.cluster.role {
                                                                                            frontend.min-nr-of-members = 1
                                                                                            backend.min-nr-of-members = $noOfBlocks
                                                                                          }"""))

    //Todo configure min nor of nodes
    def startBackend(pid: Int) = {
     val system = ActorSystem("ClusterSystem", generateConfig(pid, "backend"))
        system.actorOf(Props(new processor.CanonsProcessor(pid, noOfBlocks, blockSize)), name = "matrixProcessor")
        system.actorOf(Props[processor.MatrixStore], name = "matrixStore")
        system.awaitTermination()
    }

    object store{
          val storeCRS = ClusterRouterSettings(totalInstances = 1000, routeesPath = "/user/matrixStore", allowLocalRoutees = true, useRole = Some("backend") )
          val storeCRC = ClusterRouterConfig(SimplePortRouter(0, nrOfInstances = 100), storeCRS)
    }

}

class ClusterMultiJvmBackend1 extends FunSuite {
  test("Start backend"){
      TestConfig.startBackend(0)

  }
}

class ClusterMultiJvmBackend2 extends FunSuite {
  test("Start backend"){
      TestConfig.startBackend(1)

  }
}

class ClusterMultiJvmBackend3 extends FunSuite {
  test("Start backend"){
      TestConfig.startBackend(2)

  }
}

class ClusterMultiJvmBackend4 extends FunSuite {
  test("Start backend"){
      TestConfig.startBackend(3)
  }
}

class ClusterMultiJvmFrontend extends FunSuite {

    val expectedArrayBuffer = ArrayBuffer(2445, 2490, 2535, 2580, 2625, 2670, 2715, 2760, 2805, 2850,
                                  5766, 5892, 6018, 6144, 6270, 6396, 6522, 6648, 6774, 6900,
                                  9087, 9294, 9501, 9708, 9915, 10122, 10329, 10536, 10743, 10950,
                                  12408, 12696, 12984, 13272, 13560, 13848, 14136, 14424, 14712, 15000,
                                  15729, 16098, 16467, 16836, 17205, 17574, 17943, 18312, 18681, 19050,
                                  19050, 19500, 19950, 20400, 20850, 21300, 21750, 22200, 22650, 23100,
                                  22371, 22902, 23433, 23964, 24495, 25026, 25557, 26088, 26619, 27150,
                                  25692, 26304, 26916, 27528, 28140, 28752, 29364, 29976, 30588, 31200,
                                  0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                                  0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

  val expectedMatrix = Matrix("AxB++AxB::AxB++AxB", 10, 10, expectedArrayBuffer)

  test("Distributed multiplication of matrices should return correct result") {

    import TestConfig._
    import TestConfig.store._

    val system = ActorSystem("ClusterSystem", generateConfig(1000, "frontend"))
    lazy val storeRouter = system.actorOf(Props[processor.MatrixStore].withRouter(storeCRC), name = "matrixStoreRouter")
    lazy val facade = system.actorOf(Props[processor.WorkDisseminator].withDispatcher("work-disseminator-dispatcher"), name = "matrixFacade")
    implicit lazy val context = DMPContext(storeRouter, facade)
    lazy val A = DistributedMatrix("A", 8, 9, noOfBlocks, blockSize, ArrayBuffer(1 to 72: _*))
    lazy val B = DistributedMatrix("B", 9, 10, noOfBlocks, blockSize, ArrayBuffer(1 to 90: _*))
    var c: Matrix = Matrix.empty("r")
    var t = false
    Thread.sleep(2000)
    Cluster(system) registerOnMemberUp {
       A.persist
       B.persist
       val C = A x B
       c = C.getMatrix
      t = true
      println("RegDone!!")
    }
     //Tweak this if this test fails. calls for a FIXME
     Thread.sleep(12000)
     println("Done spinning!!"+t)
     assert(c === expectedMatrix)
     val s = "akka.tcp://ClusterSystem@127.0.0.1:255"
     (for (i <- 1 to 4) yield( s+i )) map (x => Cluster(system).down(AddressFromURIString(x)))
  }

}
