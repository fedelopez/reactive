package cat.pseudocodi.week6.kvstore

import akka.actor._

object Replicator {

  case class Replicate(key: String, valueOption: Option[String], id: Long)

  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)

  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {

  import Replicator._
  import context.dispatcher

  import scala.concurrent.duration._

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]

  var ticks = Map.empty[String, (ActorRef, Cancellable)]

  var _seqCounter = 0L

  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  def receive: Receive = {
    case Replicate(key, valueOption, seq) =>
      val msg: Snapshot = Snapshot(key, valueOption, nextSeq)
      val cancellable: Cancellable = context.system.scheduler.schedule(0.milliseconds, 100.milliseconds, replica, msg)
      ticks += key ->(sender(), cancellable)
    case SnapshotAck(key, seq) =>
      ticks.get(key).foreach((tuple: (ActorRef, Cancellable)) => {
        tuple._2.cancel()
        tuple._1 ! Replicated(key, seq)
      })
      ticks -= key
  }

}
