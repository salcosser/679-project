package assign1

 import org.apache.log4j.Logger
 import parascale.actor.last.{Actor, Remote, Task}
 import parascale.util.{getPropertyOrElse, sleep}

 /**
 * This object spawns an actor and binds it to the remote relay.
 * @author R. Coleman
 */
 object ActorB extends App {
   val LOG = Logger.getLogger(getClass)

   // Instantiating actor automatically invokes its act method.
   val actorb = new ActorB

   // Give actor time to start.
   sleep(250)

   // Assuming actor B started correctly, send test message to itself.
   // This shows that an actor can communicate locally with other actors
   // in the same JVM.
   actorb ! "testing 1-2-3"

   // Port 9000 is the default port actor B listens to
   val port: Int = getPropertyOrElse("port",9000)

   // Remote forwards inbound messages to actorb's mailbox
   new Remote(port, actorb)
   }

 /**
 * This actor awaits an inbound message from either a local or remote actor.
 */
 class ActorB extends Actor {
   import ActorB._
   def act = {
     // Wait forever to receive messages.
     while(true) {
       receive match {
       // In the LAST actor system, messages are automatically wrapped by a Task.
       case task: Task =>
         LOG.info("got task = "+task)

       // Task contains a member, payload, which is the actual serializable object sent by a LAST actor.
       task.payload match {
       // Retrieve the payload as an instance of Y
       case y: Y =>
       LOG.info("payload is Y = " + y.s)


       // Send a reply to whoever sent this message.
       sender.send("back at ya!")

       case s: String =>
       LOG.info("got "+s)
       }

       // Since "that" has no type (see reply above which has a type), in Scala
       // it's called "type erasure" which means it is an AnyRef -- semantically,
       // case that (or any name) is like Java's default switch case -- a catch-all.
       case that =>
         LOG.warn("got unexpected message: "+that)
       }
       }
     }
  }