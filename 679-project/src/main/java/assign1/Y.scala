package assign1

/**
 * This is the message sent from actor A to actor B.
 * It MUST be serializable to be transmitted as a POJO.
 * @author R. Coleman
 * @param s String message.
 */
case class Y(s: String) extends Serializable