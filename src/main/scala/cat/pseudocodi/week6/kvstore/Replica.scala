package cat.pseudocodi.week6.kvstore

import akka.actor.{Actor, ActorRef, Cancellable, Props}

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import cat.pseudocodi.week6.kvstore.Arbiter._
  import cat.pseudocodi.week6.kvstore.Persistence._
  import cat.pseudocodi.week6.kvstore.Replica._
  import cat.pseudocodi.week6.kvstore.Replicator._
  import context.dispatcher

  import scala.concurrent.duration._


  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]

  var sequence = 0
  var persistence: ActorRef = context.actorOf(persistenceProps)
  var keyToReplicator = Map.empty[String, (ActorRef, Cancellable)]

  override def preStart(): scala.Unit = {
    arbiter ! Join
  }

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  val leader: Receive = {
    case Insert(key, value, id) =>
      kv += key -> value
      sender() ! OperationAck(id)
    case Remove(key, id) =>
      kv -= key
      sender() ! OperationAck(id)
    case Get(key, id) =>
      sender() ! GetResult(key, kv.get(key), id)
  }

  val replica: Receive = {
    case Snapshot(key, value, seq) =>
      if (seq > sequence) () // ignore, not yet ready for that
      else if (seq < sequence) sender() ! SnapshotAck(key, seq)
      else {
        if (value.isEmpty) kv -= key
        else kv += key -> value.get
        val cancellable: Cancellable = context.system.scheduler.schedule(0.milliseconds, 100.milliseconds, persistence, Persist(key, value, seq))
        keyToReplicator += key ->(sender(), cancellable)
      }
    case Get(key, id) =>
      sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) =>
      keyToReplicator.get(key).foreach((tuple: (ActorRef, Cancellable)) => {
        tuple._1 ! SnapshotAck(key, sequence)
        tuple._2.cancel()
      })
      keyToReplicator -= key
      sequence += 1
  }

}

