package cat.pseudocodi.week6.kvstore

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cat.pseudocodi.week6.kvstore.Replicator.{Replicate, Replicated, Snapshot, SnapshotAck}
import org.scalactic.ConversionCheckedTripleEquals
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._

class Step3_ReplicatorSpec extends TestKit(ActorSystem("Step3ReplicatorSpec"))
with FunSuiteLike
with BeforeAndAfterAll
with Matchers
with ConversionCheckedTripleEquals
with ImplicitSender
with Tools {

  override def afterAll(): Unit = {
    system.shutdown()
  }

  test("case1: Replicator should send snapshots when asked to replicate") {
    val secondary = TestProbe()
    val replicator = system.actorOf(Replicator.props(secondary.ref), "case1-replicator")

    replicator ! Replicate("k1", Some("v1"), 0L)
    secondary.expectMsg(Snapshot("k1", Some("v1"), 0L))
    secondary.ignoreMsg({ case Snapshot(_, _, 0L) => true })
    secondary.reply(SnapshotAck("k1", 0L))

    replicator ! Replicate("k1", Some("v2"), 1L)
    secondary.expectMsg(Snapshot("k1", Some("v2"), 1L))
    secondary.ignoreMsg({ case Snapshot(_, _, 1L) => true })
    secondary.reply(SnapshotAck("k1", 1L))

    replicator ! Replicate("k2", Some("v1"), 2L)
    secondary.expectMsg(Snapshot("k2", Some("v1"), 2L))
    secondary.ignoreMsg({ case Snapshot(_, _, 2L) => true })
    secondary.reply(SnapshotAck("k2", 2L))

    replicator ! Replicate("k1", None, 3L)
    secondary.expectMsg(Snapshot("k1", None, 3L))
    secondary.reply(SnapshotAck("k1", 3L))
  }

  test("case2: Replicator should retry until acknowledged by secondary") {
    val secondary = TestProbe()
    val replicator = system.actorOf(Replicator.props(secondary.ref), "case2-replicator")

    replicator ! Replicate("k1", Some("v1"), 0L)
    secondary.expectMsg(Snapshot("k1", Some("v1"), 0L))
    secondary.expectMsg(300.milliseconds, Snapshot("k1", Some("v1"), 0L))
    secondary.expectMsg(300.milliseconds, Snapshot("k1", Some("v1"), 0L))

    secondary.reply(SnapshotAck("k1", 0L))
  }

  test("case3: Replicator should keep track of messages with same key") {
    val primary = TestProbe()
    val secondary = TestProbe()
    val replicator = system.actorOf(Replicator.props(secondary.ref), "case3-replicator")

    primary.send(replicator, Replicate("k1", Some("v1"), 0L))
    primary.send(replicator, Replicate("k1", Some("v1"), 1L))

    Thread.sleep(500)

    secondary.expectMsgAllClassOf(classOf[Snapshot])
    secondary.reply(SnapshotAck("k1", 0L))
    secondary.reply(SnapshotAck("k1", 1L))

    val actual = primary.receiveN(2).seq
    assert(actual.contains(Replicated("k1", 0L)))
    assert(actual.contains(Replicated("k1", 1L)))
  }

}
