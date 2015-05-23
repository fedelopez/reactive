package cat.pseudocodi.week6.kvstore

import akka.actor.{Actor, Props}

import scala.util.Random

/*
 * todo:
 * The provided implementation of this persistence service is a mock in the true sense, since it is rather unreliable:
 * every now and then it will fail with an exception and not acknowledge the current request.
 * It is the job of the Replica actor to create and appropriately supervise the Persistence actor;
 * for the purpose of this exercise any strategy will work, which means that you can experiment with different designs
 * based on resuming, restarting or stopping and recreating the Persistence actor.
 * To this end your Replica does not receive an ActorRef but a Props for this actor, implying that the Replica has to initially create it as well.
 */
object Persistence {

  case class Persist(key: String, valueOption: Option[String], id: Long)

  case class Persisted(key: String, id: Long)

  class PersistenceException extends Exception("Persistence failure")

  def props(flaky: Boolean): Props = Props(classOf[Persistence], flaky)
}

class Persistence(flaky: Boolean) extends Actor {

  import Persistence._

  def receive = {
    case Persist(key, _, id) =>
      if (!flaky || Random.nextBoolean()) sender ! Persisted(key, id)
      else throw new PersistenceException
  }

}
