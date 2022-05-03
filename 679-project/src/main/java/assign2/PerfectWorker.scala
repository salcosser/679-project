package assign2

import org.apache.log4j.Logger
import parascale.actor.last.{Relay, Task, Worker}
import parascale.future.perfect.FuturePerfectNumberFinder
import parascale.util._

import scala.concurrent.{Await, Future}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Spawns workers on the localhost.
  * @author Ron.Coleman
  */
object PerfectWorker extends App {
  val LOG = Logger.getLogger(getClass)

  LOG.info("started")

  // Number of hosts in this configuration
  val nhosts = getPropertyOrElse("nhosts",1)

  // One-port configuration
  val port1 = getPropertyOrElse("port", 8000)

  // If there is just one host, then the ports will include 9000 by default
  // Otherwise, if there are two hosts in this configuration, use just one
  // port which must be specified by VM options
  val ports = if(nhosts == 1) List(port1, 9000) else List(port1)

  // Spawn the worker(s).
  // Note: for initial testing with a single host, "ports" contains two ports.
  // When deploying on two hosts, "ports" will contain one port per host.
  for(port <- ports) {
    // Construction forks a thread which automatically runs the actor act method.
    new PerfectWorker(port)
  }
}

/**
  * Template worker for finding a perfect number.
  * @param port Localhost port this worker listens to
  * @author Ron.Coleman
  */
class PerfectWorker(port: Int) extends Worker(port) {
  import PerfectWorker._

  /**
    * Handles actor startup after construction.
    */
  override def act: Unit = {
    val name = getClass.getSimpleName
    LOG.info("started " + name + " (id=" + id + ")")

    // Wait for inbound messages as tasks.
    while (true) {
      receive match {


        case task: Task =>
          LOG.info("got task = " + task + " sending reply")
          task.payload match{
            case partition: Partition => {

              val RANGE = 1000000L
              //not partitioning up to the candidate, but instead the range given in the job
              val numPartitions=  ((partition.end.toDouble - partition.start.toDouble) / RANGE).ceil.toInt
              val futures = for(k <- 0 to (numPartitions-1)) yield Future{
                //need to offset the low and high bound by the start of the partition
                val lower: Long = (k * RANGE)  + partition.start
                val upper: Long = partition.end min (((k+1) * RANGE) + partition.start)
                FuturePerfectNumberFinder.sumOfFactorsInRange(lower, upper, partition.candidate)
              }

              val startTime = System.nanoTime()
              //accumulating both the partial sum and the T1 time
              val total  = futures.foldLeft((0L, 0L)) { (sum:(Long, Long), future) =>
                // Await waits for the future and unwraps the result
                val t0: Long = System.nanoTime()
                import scala.concurrent.duration._
                val result = Await.result(future, Duration.Inf)
                val t1: Long = System.nanoTime()
                // Add the partial sum into the total sum
                ((sum._1+ result).toLong, (sum._2 + (t1-t0)).toLong)
              }
              //since Result calls for a sum, start, and end,
              //our start time is the beginning of processing, and the end is start + our T1 total
              sender ! Result(total._1, startTime,startTime + total._2)
            }
          }


      }
    }
  }
}
