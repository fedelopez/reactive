package cat.pseudocodi.week6.kvstore

import akka.actor.ActorRef

/**
 * @author fede
 */

object States {

  case class UpdateKey(key: String, id: Long)

  case class PendingReplicateState(sender: ActorRef, replicators: Set[ActorRef]) {

    def removeReplicator(r: ActorRef): PendingReplicateState = {
      new PendingReplicateState(sender, replicators.filter(_ != r))
    }
  }

}
