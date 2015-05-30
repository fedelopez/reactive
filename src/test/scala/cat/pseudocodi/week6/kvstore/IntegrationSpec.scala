/**
 * Copyright (C) 2013-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package cat.pseudocodi.week6.kvstore

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cat.pseudocodi.week6.kvstore.Replica.{Get, GetResult, Insert, OperationAck}
import org.scalactic.ConversionCheckedTripleEquals
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._

class IntegrationSpec(_system: ActorSystem) extends TestKit(_system)
with FunSuiteLike
with Matchers
with BeforeAndAfterAll
with ConversionCheckedTripleEquals
with ImplicitSender
with Tools {

  def this() = this(ActorSystem("ReplicatorSpec"))

  override def afterAll: Unit = system.shutdown()

  /*
   * Recommendation: write a test case that verifies proper function of the whole system,
   * then run that with flaky Persistence and/or unreliable communication (injected by
   * using an Arbiter variant that introduces randomly message-dropping forwarder Actors).
   */
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
    val clientSecondary1 = TestProbe()
    val clientSecondary2 = TestProbe()

    clientPrimary.setAcked("key42", "42")
    clientPrimary.nothingHappens(200.millis)

    val secondary1 = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case5-secondary-1")
    val secondary2 = system.actorOf(Replica.props(arbiter, Persistence.props(flaky = true)), "case5-secondary-2")
    Thread.sleep(1000)

    for (x <- 0 to 10) {
      clientPrimary.setAcked(s"key$x", s"$x")
      clientSecondary1.send(secondary1, Get(s"key$x", x))
      clientSecondary1.expectMsg(GetResult(s"key$x", Option(s"$x"), x))
      clientSecondary2.send(secondary2, Get(s"key$x", x))
      clientSecondary2.expectMsg(GetResult(s"key$x", Option(s"$x"), x))
    }
  }
}
