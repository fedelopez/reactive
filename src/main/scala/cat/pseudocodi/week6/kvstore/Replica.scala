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

  case class Timeout(id: Long)

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor with ActorLogging {

  import cat.pseudocodi.week6.kvstore.Arbiter._
  import cat.pseudocodi.week6.kvstore.Persistence._
  import cat.pseudocodi.week6.kvstore.Replica._
  import cat.pseudocodi.week6.kvstore.Replicator._
  import cat.pseudocodi.week6.kvstore.States._
  import context.dispatcher

  import scala.concurrent.duration._

  var kv = Map.empty[String, String]
  var replicators = Set.empty[ActorRef]
  var replicaToReplicator = Map.empty[ActorRef, ActorRef]
  var persistence: ActorRef = context.actorOf(persistenceProps)
  var sequence = 0

  var pendingReplicated = List.empty[PendingReplicateState]
  var pendingPersisted = Map.empty[Long, ActorRef]
  var currentTimers = Map.empty[Long, Cancellable]

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
    case Insert(key, value, id) => persist(key, Option(value), id)
    case Remove(key, id) => persist(key, None, id)
    case Get(key, id) => sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) => handlePersisted(key, id, OperationAck(id))
    case Replicas(replicas) => replicate(replicas)
    case Replicated(key, id) => handleReplicated(key, id)
    case Timeout(id) => handleTimeout(id)
  }

  val replica: Receive = {
    case Snapshot(key, value, seq) =>
      if (seq > sequence) () // ignore, not yet ready for that
      else if (seq < sequence) sender() ! SnapshotAck(key, seq)
      else persist(key, value, nextSeq)
    case Get(key, id) => sender() ! GetResult(key, kv.get(key), id)
    case Persisted(key, id) => handlePersisted(key, id, SnapshotAck(key, id))
  }

  def persist(key: String, value: Option[String], id: Long) = {
    if (value.isDefined) kv += key -> value.get
    else kv -= key
    val cancellable: Cancellable = context.system.scheduler.schedule(Duration.Zero, 100.milliseconds, persistence, Persist(key, value, id))
    currentTimers += id -> cancellable
    pendingPersisted += id -> sender()
    context.system.scheduler.scheduleOnce(1.second, self, Timeout(id))
    forwardKeyToReplicators(id, key, value)
  }

  def handlePersisted(key: String, id: Long, msg: Any) = {
    currentTimers.get(id).foreach((cancellable: Cancellable) => cancellable.cancel())
    val pending = pendingReplicated.find((state: PendingReplicateState) => state.originalId.contains(id))
    if (pending.isEmpty) pendingPersisted.get(id).foreach((ref: ActorRef) => ref ! msg)
    currentTimers -= id
    pendingPersisted -= id
  }

  def replicate(replicas: Set[ActorRef]) = {
    val obsoleteReplicaToReplicator: Map[ActorRef, ActorRef] = replicaToReplicator.filter((tuple: (ActorRef, ActorRef)) => !replicas.contains(tuple._1))
    val obsoleteReplicators = obsoleteReplicaToReplicator.values.toSet
    obsoleteReplicators.foreach((ref: ActorRef) => context.system.stop(ref))
    cleanupObsoleteReplicatorsFromPending(obsoleteReplicators)

    val newReplicas = replicas.filter((ref: ActorRef) => ref != self).diff(replicaToReplicator.keys.toSet)
    val newReplicaToReplicator: Map[ActorRef, ActorRef] = newReplicas.map((secondary: ActorRef) => secondary -> context.actorOf(Replicator.props(secondary))).toMap

    replicaToReplicator = newReplicaToReplicator ++ replicaToReplicator.filterNot((tuple: (ActorRef, ActorRef)) => obsoleteReplicators.contains(tuple._2))
    replicators = replicaToReplicator.values.toSet

    var ids = List.empty[Long]
    replicators.foreach((replicator: ActorRef) => {
      kv.foreach((tuple: (String, String)) => {
        val seq: Long = nextSeq
        ids = seq :: ids
        replicator ! Replicate(tuple._1, Option(tuple._2), seq)
      })
    })
    pendingReplicated = new PendingReplicateState(ids, None, sender(), replicators) :: pendingReplicated
  }

  def cleanupObsoleteReplicatorsFromPending(obsoletes: Set[ActorRef]) = {
    var pendingReplicateStateCopy = List.empty[PendingReplicateState]
    obsoletes.foreach((obsolete: ActorRef) => {
      pendingReplicated.foreach((state: PendingReplicateState) => {
        val newState: PendingReplicateState = state.removeReplicator(obsolete)
        if (newState.replicators.nonEmpty) {
          pendingReplicateStateCopy = newState :: pendingReplicateStateCopy
        }
        if (state.replicators.contains(obsolete) && newState.replicators.isEmpty && state.originalId.isDefined && !pendingPersisted.contains(state.originalId.get)) {
          state.sender ! OperationAck(state.originalId.get)
        }
      })
    })
    pendingReplicated = pendingReplicateStateCopy
  }

  def handleReplicated(key: String, id: Long) = {
    val state = pendingReplicated.find(_.ids.contains(id))
    if (state.isDefined) {
      val oldState = state.get
      pendingReplicated = pendingReplicated.diff(List(oldState))
      val newState = oldState.removeReplicator(sender()).removeId(id)
      if (newState.replicators.isEmpty || newState.replicators.forall(!replicators.contains(_))) {
        if (oldState.originalId.isDefined && !oldState.originalId.exists((l: Long) => pendingPersisted.contains(l))) {
          oldState.sender ! OperationAck(oldState.originalId.get)
        }
      } else {
        pendingReplicated = newState :: pendingReplicated
      }
    }
  }

  def handleTimeout(id: Long) = {
    val find: Option[PendingReplicateState] = pendingReplicated.find(_.ids.contains(id))
    if (find.isDefined && find.get.replicators.nonEmpty && find.get.replicators.exists((ref: ActorRef) => replicators.contains(ref))) {
      find.get.sender ! OperationFailed(id)
    }
    else {
      pendingPersisted.get(id).foreach((ref: ActorRef) => ref ! OperationFailed(id))
    }
    currentTimers.get(id).foreach((cancellable: Cancellable) => cancellable.cancel())
    currentTimers -= id
    pendingPersisted -= id
    find.foreach((state: PendingReplicateState) => pendingReplicated = pendingReplicated.filterNot(_.ids.contains(id)))
  }

  def forwardKeyToReplicators(id: Long, key: String, value: Option[String]) = {
    if (replicators.nonEmpty) {
      val ids: Set[Long] = replicators.map((ref: ActorRef) => nextSeq)
      pendingReplicated = new PendingReplicateState(ids.toList, Option(id), sender(), replicators) :: pendingReplicated
      ids.zip(replicators).foreach((tuple: (Long, ActorRef)) => tuple._2 ! Replicate(key, value, tuple._1))
    }
  }

  def nextSeq: Long = {
    val ret = sequence
    sequence += 1
    ret
  }
}

