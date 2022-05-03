package assign3


import parabond.cluster.BasicNode.LOG
import parabond.cluster.{Partition, check, checkReset}
import parabond.util.{JavaMongoHelper, Result}
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
    println("basicNode")
    println("Workers: "+ sockets.length.toString)
    print("Hosts: ")
    //dynamically writing each workers addr
    (0 until workers.length).foreach { k =>
      if(k != 0){
        print(", "+ workers(k).forwardAddr)
      }else{
        print(workers(k).forwardAddr)
      }
    }
    println()
    println("Cores: "+ Runtime.getRuntime.availableProcessors)
    println("     N  missed      T1       TN     R     e")
    val TNStart = System.nanoTime()
    ladder.foreach{rung: Int => {

      val toBeChecked = parabond.cluster.checkReset(rung,0)


      val partition1 =  Partition((rung / 2), 0)
      val partition2 =  Partition(rung, (rung/2))

      val relay1 = new Relay(sockets(0), this)
      val relay2 = new Relay(sockets(1), this)

      relay1 ! partition1
      relay2 ! partition2

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
      val sumT1 = replies.foldLeft(0L){(sum, next)=>{
        sum + next
      }}

      val TN = (System.nanoTime() - TNStart)
      val TNS = (System.nanoTime() - TNStart) / 1e9d

      val T1S = sumT1 / 1e9d
      val misses = check(toBeChecked).length
      val N = Runtime.getRuntime.availableProcessors()
      val R = (sumT1.toDouble / TN.toDouble )
      val e=  (R.toDouble / N.toDouble).toDouble
      printf("%6d  %6d   %5.2f    %5.2f  %2.2f  %2.2f", rung, misses,T1S, TNS, R, e)
      println()






    }
    }
  }

}
