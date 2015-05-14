/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package cat.pseudocodi.week5.actorbintree

import akka.actor._

import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef

    def id: Int

    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection */
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}

class BinaryTreeSet extends Actor {

  import BinaryTreeSet._


  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
    case Contains(requester, id, value) => root ! Contains(requester, id, value)
    case Insert(requester, id, value) => root ! Insert(requester, id, value)
    case Remove(requester, id, value) => root ! Remove(requester, id, value)
  }
  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = ???

}

object BinaryTreeNode {

  trait Position

  case object Left extends Position

  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)

  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode], elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {

  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
    case Contains(requester, id, value) => doContains(Contains(requester, id, value))
    case Remove(requester, id, value) => doRemove(Remove(requester, id, value))
    case Insert(requester, id, value) => doInsert(Insert(requester, id, value))
  }

  def doContains(message: Contains) = {
    if (message.elem == elem && !removed) {
      message.requester ! ContainsResult(message.id, result = true)
    } else {
      if (message.elem < elem && subtrees.contains(Left)) subtrees.get(Left).get ! message
      else if (message.elem > elem && subtrees.contains(Right)) subtrees.get(Right).get ! message
      else message.requester ! ContainsResult(message.id, result = false)
    }
  }

  def doRemove(message: Remove) = {
    if (message.elem == elem && !removed) {
      removed = true
      message.requester ! OperationFinished(message.id)
    } else {
      if (message.elem < elem && subtrees.contains(Left)) subtrees.get(Left).get ! message
      else if (message.elem > elem && subtrees.contains(Right)) subtrees.get(Right).get ! message
      else message.requester ! OperationFinished(message.id)
    }
  }

  def doInsert(message: Insert) {
    if (message.elem < elem) {
      if (subtrees.get(Left).isEmpty) {
        subtrees = Map(Left -> context.actorOf(BinaryTreeNode.props(message.elem, initiallyRemoved = false)), Right -> subtrees.get(Right).orNull)
        message.requester ! OperationFinished(message.id)
      } else {
        subtrees.get(Left).get ! message
      }
    } else if (message.elem > elem) {
      if (subtrees.get(Right).isEmpty) {
        subtrees = Map(Left -> subtrees.get(Left).orNull, Right -> context.actorOf(BinaryTreeNode.props(message.elem, initiallyRemoved = false)))
        message.requester ! OperationFinished(message.id)
      } else {
        subtrees.get(Right).get ! message
      }
    } else message.requester ! OperationFinished(message.id)

  }


  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

}
