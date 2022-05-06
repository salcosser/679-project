package assign3


import parabond.cluster.BasicNode.LOG
import parabond.cluster.{Partition, check, checkReset}
import parabond.util.{JavaMongoHelper, MongoHelper, Result}
import parascale.actor.last.{Dispatcher, Relay, Task}
import parascale.future.perfect.candidates
import parascale.util.getPropertyOrElse

object ParaDispatcher extends App {
  // For initial testing on a single host, use this socket.
  // When deploying on multiple hosts, use the VM argument,
  // -Dsocket=<ip address>:9000 which points to the second
  // host.
  val socket2 = getPropertyOrElse("socket","localhost:9000")

  //hushing mongoDB
  JavaMongoHelper.hush()
  // This spawns a list of relay workers at the sockets
  new ParaDispatcher(List("localhost:8000", socket2))
}

class ParaDispatcher(sockets: List[String]) extends Dispatcher(sockets: List[String]){
  def act(): Unit ={
//    val ladder = List(
//      1000,
//      2000,
//      4000,
//      8000,
//      16000,
//      32000,
//      64000,
//      100000 )

   val ladder = List(100,200,300)
    println("ParaBond Analysis")
    println("By Sam Alcosser")
    println(java.time.LocalDate.now)
    println("FineGrainedNode")
    println("Workers: "+ sockets.length.toString)
    print("Hosts: ")
    //dynamically writing each hosts address
    print(workers(0).forwardAddr + " (dispatcher)")
    (0 until workers.length).foreach { k =>
        print(", "+ workers(k).forwardAddr + " (worker)")
    }
    print(", "+ MongoHelper.getHost + " (mongo)")
    println()
    println("Cores: "+ (Runtime.getRuntime.availableProcessors + 4))
    println("     N  missed      T1       TN     R     e")

    ladder.foreach{rung: Int => {
      val TNStart = System.nanoTime()
      //resetting the values that may not be -1
      val toBeChecked = parabond.cluster.checkReset(rung,0)

      //splitting the check ranges between the two workers
      val partition1 =  Partition((rung / 2), 1)
      val partition2 =  Partition((rung / 2), ((rung/2)+1))

      val relay1 = new Relay(sockets(0), this)
      val relay2 = new Relay(sockets(1), this)

      relay1 ! partition1
      relay2 ! partition2
      //waiting for replies from workers
      val replies = for(_ <- 0 until sockets.length) yield
          receive match {
            case task: Task if task.kind == Task.REPLY =>
              task.payload match{
                case result: Result=>{
                  //accumulating the values
                  result.t1 - result.t0
                }
              }
          }
      //adding up t1 values from all (both) workers
      val sumT1 = replies.foldLeft(0L){(sum, next)=>{
        sum + next
      }}

      val TN = (System.nanoTime() - TNStart)
      val TNS = (System.nanoTime() - TNStart) / 1e9d
      val T1S = sumT1 / 1e9d
      val misses = check(toBeChecked).length
      val N = Runtime.getRuntime.availableProcessors() + 4
      val R = (sumT1.toDouble / TN.toDouble )
      val e=  (R.toDouble / N.toDouble).toDouble

      printf("%6d %7d %7.2f %8.2f  %2.2f  %2.2f", rung, misses,T1S, TNS, R, e)
      println()






    }
    }
  }

}
