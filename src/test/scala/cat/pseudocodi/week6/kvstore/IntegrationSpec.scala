/**
 * Copyright (C) 2013-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package cat.pseudocodi.week6.kvstore

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalactic.ConversionCheckedTripleEquals
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

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
}
