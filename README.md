# DMP Distributed Matrix Processor.

 It uses akka cluster for message passing and cluster managment/routing etc.

## To try out follow these steps.

* Setup: For quick trying out purposes and minimal configuration a few nodes can be started locally.

    `$ sbt dist`

   * After this copy the generated `dist` in `target/dist` to your convenient location and replace bin/* with scripts/*

   * Then start **four** different nodes by doing this in four different terminals.

     `$ bin/start kernel.Backend 0`

     `$ bin/start kernel.Backend 1`

     `$ bin/start kernel.Backend 2`

     `$ bin/start kernel.Backend 3`


* Using:  Once backend nodes are running. You connect via sbt console and try multiplying example matrices.

     `$ sbt> console`


_...... prints logo and some commands ......_

     `scala> import scala.collection.mutable.ArrayBuffer`

     `scala> val A = DistributedMatrix("A", 8, 9, noOfBlocks, blockSize, ArrayBuffer(1 to 72: _*))`

     `scala> val B = DistributedMatrix("B", 8, 9, noOfBlocks, blockSize, ArrayBuffer(1 to 72: _*))`

     `scala> A.persist //Will persist the sample matrix A`

     `scala> B.persist //Similiarly will persist the sample matrix B`

     `scala> val C = A x B // this will multiply them, and it happens on the cluster not local.`

     `scala> val C = C x B // this will again multiply them.`

     `scala> C.getMatrix // To see the content of the matrix and get a local matrix from DM.`


# Thanks!


_Please open issues to share opinions. At the moment have no mailing lists setup etc._
