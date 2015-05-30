/**
 * Copyright (C) 2013-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package cat.pseudocodi.week6.kvstore

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cat.pseudocodi.week6.kvstore.Arbiter.{Join, JoinedPrimary, JoinedSecondary, Replicas}
import cat.pseudocodi.week6.kvstore.Replica.{Get, GetResult, Insert, OperationAck}
import cat.pseudocodi.week6.kvstore.Replicator.Snapshot
import org.scalactic.ConversionCheckedTripleEquals
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._

/*
 * Recommendation: write a test case that verifies proper function of the whole system,
 * then run that with flaky Persistence and/or unreliable communication (injected by
 * using an Arbiter variant that introduces randomly message-dropping forwarder Actors).
 */
class IntegrationSpec(_system: ActorSystem) extends TestKit(_system)
with FunSuiteLike
with Matchers
with BeforeAndAfterAll
with ConversionCheckedTripleEquals
with ImplicitSender
with Tools {

  def this() = this(ActorSystem("ReplicatorSpec"))

  override def afterAll: Unit = system.shutdown()

  test("case1: Primary should react properly to Insert, Remove, Get") {
    val arbiter = system.actorOf(Props.create(classOf[Arbiter]), "case1-arbiter")
    val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = false)), "case1-primary")
    val client = session(primary)

    client.getAndVerify("k1")
    client.setAcked("k1", "v1")
    client.getAndVerify("k1")
    client.getAndVerify("k2")
    client.setAcked("k2", "v2")
    client.getAndVerify("k2")
    client.removeAcked("k1")
    client.getAndVerify("k1")
  }

  test("case2: Primary should react properly to Insert with flaky") {
    val arbiter = system.actorOf(Props.create(classOf[Arbiter]), "case2-arbiter")
    val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case2-primary")

    val client = TestProbe()

    for (x <- 1 to 10) {
      client.send(primary, Insert(s"k$x", s"val$x", x))
      client.expectMsg(OperationAck(x))
    }
  }

  test("case3: Secondary should react properly to Get") {
    val arbiter = system.actorOf(Props.create(classOf[Arbiter]), "case3-arbiter")
    val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case3-primary")

    val client = TestProbe()

    client.send(primary, Insert("k1", "val1", 0))
    client.expectMsg(OperationAck(0))

    client.send(primary, Get("k1", 1))
    client.expectMsg(GetResult("k1", Option("val1"), 1))

    val secondary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case3-secondary")
    Thread.sleep(200)
    client.send(secondary, Get("k1", 0))
    client.expectMsg(GetResult("k1", Option("val1"), 0))
  }

  test("case4: Primary should react properly to Insert with secondary") {
    val arbiter = system.actorOf(Props.create(classOf[Arbiter]), "case4-arbiter")
    val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case4-primary")
    val clientPrimary = session(primary)
    val clientSecondary = TestProbe()

    clientPrimary.setAcked("key42", "42")

    val secondary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case4-secondary")
    Thread.sleep(1000)

    clientSecondary.send(secondary, Get("key42", 0))
    clientSecondary.expectMsg(GetResult("key42", Option("42"), 0))

    clientPrimary.setAcked("key43", "43")
    clientSecondary.send(secondary, Get("key43", 1))
    clientSecondary.expectMsg(GetResult("key43", Option("43"), 1))

    clientPrimary.setAcked("key44", "44")
    clientSecondary.send(secondary, Get("key44", 2))
    clientSecondary.expectMsg(GetResult("key44", Option("44"), 2))

    clientPrimary.setAcked("key45", "45")
    clientSecondary.send(secondary, Get("key45", 3))
    clientSecondary.expectMsg(GetResult("key45", Option("45"), 3))
  }

  test("case5: Primary should react properly to Insert with secondaries") {
    val arbiter = system.actorOf(Props.create(classOf[Arbiter]), "case5-arbiter")
    val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case5-primary")
    val clientPrimary = session(primary)

    clientPrimary.setAcked("key42", "42")
    clientPrimary.nothingHappens(200.millis)

    val clientSecondary1 = session(system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case5-secondary-1"))
    val clientSecondary2 = session(system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case5-secondary-2"))
    Thread.sleep(1000)

    for (x <- 0 to 10) {
      clientPrimary.setAcked(s"key$x", s"$x")
      assert(Some(s"$x") === clientSecondary1.get(s"key$x"))
      assert(Some(s"$x") === clientSecondary2.get(s"key$x"))
    }
  }

  test("case6: Primary should react properly to Delete with secondaries") {
    val arbiter = system.actorOf(Props.create(classOf[Arbiter]), "case6-arbiter")
    val primary = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case6-primary")
    val clientPrimary = session(primary)
    val clientSecondary1 = TestProbe()
    val clientSecondary2 = TestProbe()

    clientPrimary.setAcked("key42", "42")
    clientPrimary.nothingHappens(200.millis)

    val secondary1 = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case6-secondary-1")
    val secondary2 = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case6-secondary-2")
    Thread.sleep(1000)

    clientPrimary.removeAcked("key42")
    clientSecondary1.send(secondary1, Get("42", 11))
    clientSecondary1.expectMsg(GetResult("42", None, 11))
    clientSecondary2.send(secondary2, Get("42", 11))
    clientSecondary2.expectMsg(GetResult("42", None, 11))
  }

  test("case7: Primary and secondaries must work in concert when persistence and communication to secondaries is unreliable") {
    val arbiter = TestProbe()
    val (primary, user) = createPrimary(arbiter, "case7-primary", flakyPersistence = true)

    user.setAcked("k1", "v1")

    val (secondary1, replica1) = createSecondary(arbiter, "case7-secondary1", flakyForwarder = true, flakyPersistence = true)
    val (secondary2, replica2) = createSecondary(arbiter, "case7-secondary2", flakyForwarder = true, flakyPersistence = true)

    arbiter.send(primary, Replicas(Set(primary, secondary1, secondary2)))

    val options = Set(None, Some("v1"))
    options should contain(replica1.get("k1"))
    options should contain(replica2.get("k1"))

    user.setAcked("k1", "v2")
    assert(replica1.get("k1") === Some("v2"))
    assert(replica2.get("k1") === Some("v2"))

    val (secondary3, replica3) = createSecondary(arbiter, "case7-secondary3", flakyForwarder = true, flakyPersistence = true)

    arbiter.send(primary, Replicas(Set(primary, secondary1, secondary3)))

    replica3.nothingHappens(500.milliseconds)

    assert(replica3.get("k1") === Some("v2"))

    user.removeAcked("k1")
    assert(replica1.get("k1") === None)
    assert(replica2.get("k1") === Some("v2"))
    assert(replica3.get("k1") === None)

    user.setAcked("k1", "v4")
    assert(replica1.get("k1") === Some("v4"))
    assert(replica2.get("k1") === Some("v2"))
    assert(replica3.get("k1") === Some("v4"))

    user.setAcked("k2", "v1")
    user.setAcked("k3", "v1")

    user.setAcked("k1", "v5")
    user.removeAcked("k1")
    user.setAcked("k1", "v7")
    user.removeAcked("k1")
    user.setAcked("k1", "v9")
    assert(replica1.get("k1") === Some("v9"))
    assert(replica2.get("k1") === Some("v2"))
    assert(replica3.get("k1") === Some("v9"))

    assert(replica1.get("k2") === Some("v1"))
    assert(replica2.get("k2") === None)
    assert(replica3.get("k2") === Some("v1"))

    assert(replica1.get("k3") === Some("v1"))
    assert(replica2.get("k3") === None)
    assert(replica3.get("k3") === Some("v1"))
  }

  def createPrimary(arbiter: TestProbe, name: String, flakyPersistence: Boolean) = {
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.props(flakyPersistence)), name)

    arbiter.expectMsg(Join)
    arbiter.send(primary, JoinedPrimary)

    (primary, session(primary))
  }

  def createSecondary(arbiter: TestProbe, name: String, flakyForwarder: Boolean, flakyPersistence: Boolean) = {
    val secondary =
      if (flakyForwarder) system.actorOf(Props(new FlakyForwarder(Replica.props(arbiter.ref, Persistence.props(flakyPersistence)), name)), s"flaky-$name")
      else system.actorOf(Replica.props(arbiter.ref, Persistence.props(flakyPersistence)), name)

    arbiter.expectMsg(Join)
    arbiter.send(secondary, JoinedSecondary)

    (secondary, session(secondary))
  }

  class FlakyForwarder(targetProps: Props, name: String) extends Actor with ActorLogging {
    val child = context.actorOf(targetProps, name)
    var flipFlop = true

    def receive = {
      case msg if sender == child =>
        context.parent forward msg

      case msg: Snapshot =>
        if (flipFlop) child forward msg
        else log.debug(s"Dropping $msg")
        flipFlop = !flipFlop

      case msg =>
        child forward msg
    }
  }
}
