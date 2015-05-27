package cat.pseudocodi.week6.kvstore

import akka.actor.SupervisorStrategy.Resume
import akka.actor._

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  case class Timeout(key: String, id: Long)

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
  import cat.pseudocodi.week6.kvstore.States._
  import context.dispatcher

  import scala.concurrent.duration._

  var kv = Map.empty[String, String]
  var replicators = Set.empty[ActorRef]

  var pendingReplicated = Map.empty[UpdateKey, PendingReplicateState]
  var pendingPersisted = Map.empty[UpdateKey, ActorRef]
  var keyToSender = Map.empty[UpdateKey, (ActorRef, Cancellable)]

  var sequence = 0
  var persistence: ActorRef = context.actorOf(persistenceProps)

  override val supervisorStrategy = OneForOneStrategy() {
    case _: PersistenceException => Resume
  }

  override def preStart(): scala.Unit = {
    arbiter ! Join
  }

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  val leader: Receive = {
    case Insert(key, value, id) => handleUpdate(key, Option(value), id)
    case Remove(key, id) => handleUpdate(key, None, id)
    case Get(key, id) => sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) => handlePersisted(key, id)
    case Replicas(replicas) => handleReplicas(replicas)
    case Replicated(key, id) => handleReplicatedOK(key, id)
    case Timeout(key, id) => handleTimeout(key, id)
  }

  val replica: Receive = {
    case Snapshot(key, value, seq) =>
      if (seq > sequence) () // ignore, not yet ready for that
      else if (seq < sequence) sender() ! SnapshotAck(key, seq)
      else {
        if (value.isDefined) kv += key -> value.get
        else kv -= key
        val cancellable: Cancellable = context.system.scheduler.schedule(Duration.Zero, 100.milliseconds, persistence, Persist(key, value, seq))
        keyToSender += new UpdateKey(key, seq) ->(sender(), cancellable)
      }
    case Get(key, id) =>
      sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) =>
      val updateKey = new UpdateKey(key, id)
      keyToSender.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => {
        tuple._1 ! SnapshotAck(key, sequence)
        tuple._2.cancel()
      })
      keyToSender -= updateKey
      sequence += 1
  }

  def handleUpdate(key: String, value: Option[String], id: Long) = {
    if (value.isDefined) kv += key -> value.get
    else kv -= key

    val updateKey = new UpdateKey(key, id)
    scheduleTimeoutForUpdate(updateKey)
    val cancellable: Cancellable = context.system.scheduler.schedule(Duration.Zero, 100.milliseconds, persistence, Persist(updateKey.key, value, updateKey.id))
    keyToSender += updateKey ->(sender(), cancellable)
    forwardKeyToReplicators(key, value)
  }

  def scheduleTimeoutForUpdate(updateKey: UpdateKey) = {
    if (replicators.nonEmpty) pendingReplicated += updateKey -> new PendingReplicateState(sender(), replicators)
    pendingPersisted += updateKey -> sender()
    context.system.scheduler.scheduleOnce(1.second, self, Timeout(updateKey.key, updateKey.id))
  }

  def handlePersisted(key: String, id: Long) = {
    val updateKey = new UpdateKey(key, id)
    pendingPersisted -= updateKey
    keyToSender.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => {
      if (!pendingReplicated.contains(updateKey)) tuple._1 ! OperationAck(id)
      tuple._2.cancel()
    })
    keyToSender -= updateKey
  }

  def handleReplicas(replicas: Set[ActorRef]) = {
    val obsolete: Set[ActorRef] = replicators.diff(replicas)
    removeObsoleteReplicators(obsolete)
    obsolete.foreach((ref: ActorRef) => ref ! PoisonPill)
    replicators = replicas.filter((ref: ActorRef) => ref != self).map((secondary: ActorRef) => context.actorOf(Replicator.props(secondary)))
    replicators.foreach((replicator: ActorRef) => {
      var seq = 0
      kv.foreach((tuple: (String, String)) => {
        replicator ! Replicate(tuple._1, Option(tuple._2), seq)
        seq += 1
      })
    })
  }

  def removeObsoleteReplicators(obsolete: Set[ActorRef]) = {
    var pendingReplicateStateCopy = Map.empty[UpdateKey, PendingReplicateState]
    obsolete.foreach((ref: ActorRef) => {
      pendingReplicated.foreach((tuple: (UpdateKey, PendingReplicateState)) => {
        val state: PendingReplicateState = tuple._2.removeReplicator(ref)
        if (state.replicators.nonEmpty) {
          pendingReplicateStateCopy += tuple._1 -> state
        }
        if (tuple._2.replicators.contains(ref) && state.replicators.isEmpty && !pendingPersisted.contains(tuple._1)) {
          tuple._2.sender ! OperationAck(tuple._1.id)
        }
      })
    })
    pendingReplicated = pendingReplicateStateCopy
  }

  def handleReplicatedOK(key: String, id: Long) = {
    val updateKey: UpdateKey = new UpdateKey(key, id)
    if (pendingReplicated.contains(updateKey)) {
      var state = pendingReplicated.get(updateKey).get
      state = state.removeReplicator(sender())
      if (state.replicators.isEmpty) {
        pendingReplicated -= updateKey
        if (!pendingPersisted.contains(updateKey)) state.sender ! OperationAck(id)
      } else {
        pendingReplicated += updateKey -> state
      }
    }
  }

  def handleTimeout(key: String, id: Long) = {
    val updateKey: UpdateKey = new UpdateKey(key, id)
    if (pendingReplicated.contains(updateKey) && pendingReplicated.get(updateKey).get.replicators.nonEmpty) {
      pendingReplicated.get(updateKey).get.sender ! OperationFailed(id)
    }
    else {
      keyToSender.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => tuple._1 ! OperationFailed(updateKey.id))
    }
    keyToSender.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => tuple._2.cancel())
    keyToSender -= updateKey
    pendingReplicated -= updateKey
  }

  def forwardKeyToReplicators(key: String, value: Option[String]) = {
    var seq = 0
    replicators.foreach((replicator: ActorRef) => {
      replicator ! Replicate(key, value, seq)
      seq += 1
    })
  }

}

