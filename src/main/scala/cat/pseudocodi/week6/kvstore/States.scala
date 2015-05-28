package cat.pseudocodi.week6.kvstore

import akka.actor.ActorRef

/**
 * @author fede
 */

object States {

  case class PendingReplicateState(ids: List[Long], originalId: Option[Long], sender: ActorRef, replicators: Set[ActorRef]) {

    def removeReplicator(r: ActorRef): PendingReplicateState = {
      new PendingReplicateState(ids, originalId, sender, replicators.filter(_ != r))
    }

    def removeId(id: Long): PendingReplicateState = {
      new PendingReplicateState(ids.filter(_ != id), originalId, sender, replicators)
    }
  }

}
