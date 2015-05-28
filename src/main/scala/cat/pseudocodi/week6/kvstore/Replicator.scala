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

  // map from sequence number to pair of sender and request
  var pendingAcks = Map.empty[Long, (ActorRef, Replicate, Cancellable)]
  var _seqCounter = 0L

  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }

  def receive: Receive = {
    case Replicate(key, valueOption, id) =>
      val seq: Long = nextSeq
      val msg: Snapshot = Snapshot(key, valueOption, seq)
      val cancellable: Cancellable = context.system.scheduler.schedule(Duration.Zero, 100.milliseconds, replica, msg)
      pendingAcks += seq ->(sender(), Replicate(key, valueOption, id), cancellable)
    case SnapshotAck(key, seq) =>
      pendingAcks.get(seq).foreach((tuple: (ActorRef, Replicate, Cancellable)) => {
        tuple._3.cancel()
        tuple._1 ! Replicated(key, tuple._2.id)
      })
      pendingAcks -= seq
  }

}
