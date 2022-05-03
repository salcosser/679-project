package assign3

import parabond.cluster.{Node, Partition}
import parabond.util.{JavaMongoHelper, Result}
import parascale.actor.last.{Task, Worker}
import parascale.util.getPropertyOrElse

object ParaWorker extends App {
  //hushing mongoDB
  JavaMongoHelper.hush()
  //a. If worker running on a single host, spawn two workers
  //   else spawn one worker.
  val nhosts = getPropertyOrElse("nhosts", 1)

// One-port configuration
val port1 = getPropertyOrElse("port", 8000)

  // If there is 1 host, then ports include 9000 by default
  // Otherwise, if there are two hosts in this configuration,
  // use just one port which must be specified by VM options
  val ports =
  if (nhosts == 1) List(port1, 9000) else List(port1)

  // Spawn the worker(s).
  // Note: for initial testing with a single host, "ports"
  // contains two ports. When deploying on two hosts, "ports"
  // will contain one port per host.
  for (port <- ports) {
    // Start a new worker.
    new ParaWorker(port)
  }
}


class ParaWorker(port: Int) extends Worker(port) {

  import ParaWorker._



  override def act: Unit = {
    while (true) {
      receive match {


        case task: Task =>
          // LOG.info("got task = " + task + " sending reply")
          task.payload match {
            case partition: Partition => {
              val node = Node.getInstance(partition)
              assert(node != null, "failed to construct node")
              val analysis = node.analyze()
              val sumT1 = analysis.results.foldLeft(0L) { (sum, next) => {
                val currentResult = next.result
                sum + (currentResult.t1 - currentResult.t0)
              }

              }
              sender ! Result(sumT1)
            }


          }
      }
    }

  }
}
