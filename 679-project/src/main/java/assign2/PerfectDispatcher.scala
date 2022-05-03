package assign2

import org.apache.log4j.Logger
import parascale.actor.last.{Dispatcher, Relay, Task}
import parascale.util._
import parascale.future.perfect
import parascale.future.perfect.candidates

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

/**
  * Spawns a dispatcher to connect to multiple workers.
  * @author Ron.Coleman
  */
object PerfectDispatcher extends App {
  val LOG = Logger.getLogger(getClass)
  LOG.info("started")

  // For initial testing on a single host, use this socket.
  // When deploying on multiple hosts, use the VM argument,
  // -Dsocket=<ip address>:9000 which points to the second
  // host.
  val socket2 = getPropertyOrElse("socket","localhost:9000")

  // Construction forks a thread which automatically runs the actor act method.
  new PerfectDispatcher(List("localhost:8000", socket2))
}

/**
  * Template dispatcher which tests readiness of the host(s)
  * @param sockets List of sockets
  * @author Ron.Coleman
  */
class PerfectDispatcher(sockets: List[String]) extends Dispatcher(sockets) {
  import PerfectDispatcher._

  /**
    * Handles actor startup after construction.
    */
  def act: Unit = {
    LOG.info("sockets to workers = "+sockets)

    //printing report information
    println("PNF Using Futures")
    println("By Sam Alcosser")
    println(java.time.LocalDate.now)
    println("Cores: "+ Runtime.getRuntime.availableProcessors)
    print("Hosts: ")
    //dynamically writing each workers addr
    (0 until workers.length).foreach { k =>
      if(k != 0){
        print(", "+ workers(k).forwardAddr)
      }else{
        print(workers(k).forwardAddr)
      }
      //print(" "+ workers(k).
    }
    println()
    println("Candidate      Perfect   T1(s)     TN(s)     R     e")

    //each iteration is a different candidate number
    (0 until candidates.length-1).foreach{ index =>

      //full range of values is split among each worker, therefore this could work with >2 workers
      val RANGE = (candidates(index).toDouble / workers.length).ceil.toLong
      //one future per socket
      val futures = for(k <- 0 until workers.length) yield Future{
        val lower: Long = (k * RANGE) + 1
        val upper: Long = candidates(index) min ((k+1) * RANGE)
        val relay = new Relay(sockets(k), this)
        relay ! Partition(lower,upper, candidates(index))
      }

      val parStartTime = System.nanoTime()
      //sending out the partitions by running the futures
      for(future <- futures){
        import scala.concurrent.duration._
        val result = Await.result(future, Duration.Inf)
      }
      //accumulator variables needed as there is no way to know when a worker will respond, or the order
      var receivedResults = 0
      var tSum = 0.0
      var T1 = 0.0
      //only hold the listener open until all futures have given a response, then move on
      while (receivedResults < futures.length) {
        receive match {
          case task: Task if task.kind == Task.REPLY =>
            LOG.info("received reply " + task)
            task.payload match{
              case result: Result=>{
                //accumulating the values
                tSum = tSum + result.sum
                T1 = T1 + (result.t1 - result.t0)
                receivedResults = receivedResults + 1

              }
            }

        }
      }
      //calculations needed for logging results to report
      val parTime = System.nanoTime() -  parStartTime
      val N = Runtime.getRuntime.availableProcessors
      val thisCandidate = candidates(index)
      val TN = parTime / 1e9d
      val T1S = T1 / 1e9d
      val R = (T1.toDouble / parTime.toDouble).toDouble
      // avoiding int division by converting N to a double
      val e = (R.toDouble / N.toDouble).toDouble
      if((2 * candidates(index)) == tSum){
        printf("%-13d  YES      %6.2f    %6.2f  %1.2f  %1.2f", thisCandidate, T1S, TN, R, e)
        println()
      }else{
        printf("%-13d  no       %6.2f    %6.2f  %1.2f  %1.2f", thisCandidate, T1S, TN, R, e)
        println()
      }


    }





  }
}

