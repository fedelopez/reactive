package cat.pseudocodi.week6.kvstore

import akka.actor._

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
  import cat.pseudocodi.week6.kvstore.States._
  import context.dispatcher

  import scala.concurrent.duration._

  var kv = Map.empty[String, String]
  var secondaryToReplicator = Map.empty[ActorRef, ActorRef]
  var replicators = Set.empty[ActorRef]

  var pendingReplicated = Map.empty[UpdateKey, PendingReplicateState]
  var pendingPersisted = Map.empty[UpdateKey, ActorRef]
  var keyToReplicator = Map.empty[UpdateKey, (ActorRef, Cancellable)]
  var keyToCount = Map.empty[UpdateKey, Int]

  var sequence = 0
  var persistence: ActorRef = context.actorOf(persistenceProps)

  override def preStart(): scala.Unit = {
    arbiter ! Join
  }

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  val leader: Receive = {
    case Insert(key, value, id) => handleInsert(key, value, id)
    case Remove(key, id) => handleRemove(key, id)
    case Get(key, id) => sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) => handlePersisted(key, id)
    case Replicas(replicas) => handleReplicas(replicas)
    case Replicated(key, id) => handleReplicatedOK(key, id)
    case ReplicateTimeout(key, id) => handleReplicateTimeout(key, id)
  }

  val replica: Receive = {
    case Snapshot(key, value, seq) =>
      if (seq > sequence) () // ignore, not yet ready for that
      else if (seq < sequence) sender() ! SnapshotAck(key, seq)
      else {
        if (value.isEmpty) kv -= key
        else kv += key -> value.get
        val cancellable: Cancellable = context.system.scheduler.schedule(Duration.Zero, 100.milliseconds, persistence, Persist(key, value, seq))
        keyToReplicator += new UpdateKey(key, seq) ->(sender(), cancellable)
      }
    case Get(key, id) =>
      sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) =>
      val updateKey = new UpdateKey(key, id)
      keyToReplicator.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => {
        tuple._1 ! SnapshotAck(key, sequence)
        tuple._2.cancel()
      })
      keyToReplicator -= updateKey
      sequence += 1
  }

  def handleInsert(key: String, value: String, id: Long) = {
    val updateKey = new UpdateKey(key, id)
    scheduleTimeoutForUpdate(updateKey)
    kv += key -> value
    handlePersist(updateKey, Option(value))
    forwardKeyValuesToReplicators()
  }

  def handlePersist(updateKey: UpdateKey, value: Option[String]): Unit = {
    val cancellable: Cancellable = context.system.scheduler.schedule(Duration.Zero, 100.milliseconds) {
      val count: Int = keyToCount.getOrElse(updateKey, 0)
      if (count > 9) {
        keyToReplicator.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => {
          tuple._1 ! OperationFailed(updateKey.id)
          tuple._2.cancel()
        })
        keyToCount -= updateKey
        keyToReplicator -= updateKey
      }
      else {
        keyToCount += updateKey -> (count + 1)
        persistence ! Persist(updateKey.key, value, updateKey.id)
      }
    }
    keyToReplicator += updateKey ->(sender(), cancellable)
  }

  def scheduleTimeoutForUpdate(updateKey: UpdateKey) = {
    if (replicators.nonEmpty) pendingReplicated += updateKey -> new PendingReplicateState(sender(), replicators)
    pendingPersisted += updateKey -> sender()
    context.system.scheduler.scheduleOnce(1.second, self, ReplicateTimeout(updateKey.key, updateKey.id))
  }

  def handleRemove(key: String, id: Long) = {
    val updateKey: UpdateKey = new UpdateKey(key, id)
    scheduleTimeoutForUpdate(updateKey)
    kv -= key
    handlePersist(updateKey, None)
    forwardDeleteKeyToReplicators(key)
  }

  def handlePersisted(key: String, id: Long) = {
    val updateKey: UpdateKey = new UpdateKey(key, id)
    pendingPersisted -= updateKey
    keyToReplicator.get(updateKey).foreach((tuple: (ActorRef, Cancellable)) => {
      if (!pendingReplicated.contains(updateKey)) tuple._1 ! OperationAck(id)
      tuple._2.cancel()
    })
    keyToCount -= updateKey
    keyToReplicator -= updateKey
  }

  def handleReplicas(replicas: Set[ActorRef]) = {
    val obsoleteReplicas: Map[ActorRef, ActorRef] = secondaryToReplicator.filter((tuple: (ActorRef, ActorRef)) => !replicas.contains(tuple._2))
    obsoleteReplicas.foreach((tuple: (ActorRef, ActorRef)) => {
      tuple._2 ! PoisonPill //kill replicator
    })
    secondaryToReplicator = replicas.filter((ref: ActorRef) => ref != self).map((secondary: ActorRef) => secondary -> context.actorOf(Replicator.props(secondary))).toMap
    replicators = secondaryToReplicator.values.toSet
    forwardKeyValuesToReplicators()
  }

  def handleReplicatedOK(key: String, id: Long): Unit = {
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

  def handleReplicateTimeout(key: String, id: Long): Unit = {
    val updateKey: UpdateKey = new UpdateKey(key, id)
    if (pendingReplicated.contains(updateKey) && pendingReplicated.get(updateKey).get.replicators.nonEmpty)
      pendingReplicated.get(updateKey).get.sender ! OperationFailed(id)
  }

  def forwardKeyValuesToReplicators() = {
    replicators.foreach((replicator: ActorRef) => {
      var seq = 0
      kv.foreach((tuple: (String, String)) => {
        replicator ! Replicate(tuple._1, Option(tuple._2), seq)
        seq += 1
      })
    })
  }

  def forwardDeleteKeyToReplicators(key: String) = {
    var seq = 0
    replicators.foreach((replicator: ActorRef) => {
      replicator ! Replicate(key, None, seq)
      seq += 1
    })
  }

}

