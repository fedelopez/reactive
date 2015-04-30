package cat.pseudocodi.week3

import cat.pseudocodi.week3.NodeScala._
import nodescala._
import org.scalatest._

import scala.async.Async.async
import scala.collection._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps

class NodeScalaSuite extends FunSuite {

  test("A Future should always be completed") {
    val always = Future.always(517)
    assert(Await.result(always, 0 nanos) == 517)
  }

  test("A Future should never be completed") {
    val never = Future.never[Int]
    try {
      Await.result(never, 1 second)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  test("should return the future holding the list of values of all the futures from the list") {
    val all: Future[List[Int]] = Future.all(List(Future.always(1), Future.always(3)))
    val result: List[Int] = Await.result(all, 0 nanos)
    assert(result == List(1, 3))
  }

  test("should return the failing future when one fails on singleton list") {
    val all: Future[List[Int]] = Future.all(List(Future.failed(new Exception("Doh!"))))
    all onFailure {
      case e => assert(all.isCompleted)
    }
    all onSuccess {
      case _ => fail("Should be on error")
    }

    Await.ready(all, 0 nanos)
  }

  test("should return the failing future when one fails") {
    val all: Future[List[Int]] = Future.all(List(Future.always(1), Future.failed(new Exception()), Future.always(3)))
    all onFailure {
      case e => assert(all.isCompleted)
    }
    all onSuccess {
      case _ => fail("Should be on error")
    }

    Await.ready(all, 0 nanos)
  }

  test("should return the first completed future") {
    val any: Future[Int] = Future.any(List(Future.never[Int], Future.never[Int], Future.always(3)))
    val result: Int = Await.result(any, 1 second)
    assert(3 === result)
  }

  test("should return the first completed future even if on error") {
    val any: Future[Int] = Future.any(List(Future.never[Int], Future.failed(new Exception("Doh!")), Future.never[Int]))
    any onFailure {
      case e => assert(any.isCompleted)
    }
    any onSuccess {
      case _ => fail("Should be on error")
    }

    Await.ready(any, 1 second)
  }

  test("should delay") {
    val delay: Future[Unit] = Future.delay(3 seconds)
    delay onSuccess {
      case e => assert(delay.isCompleted)
    }
    delay onFailure {
      case _ => fail("Should have completed")
    }
    Await.result(delay, 3 seconds)
  }

  test("should fail as delay is not enough") {
    val delay: Future[Unit] = Future.delay(3 seconds)
    try {
      Await.result(delay, 1 seconds)
      assert(false)
    } catch {
      case t: TimeoutException => // ok!
    }
  }

  test("CancellationTokenSource should allow stopping the computation") {
    val cts = CancellationTokenSource()
    val ct = cts.cancellationToken
    val p = Promise[String]()

    async {
      while (ct.nonCancelled) {
        // do work
      }

      p.success("done")
    }

    cts.unsubscribe()
    assert(Await.result(p.future, 1 second) == "done")
  }

  class DummyExchange(val request: Request) extends Exchange {
    @volatile var response = ""
    val loaded = Promise[String]()

    def write(s: String) {
      response += s
    }

    def close() {
      loaded.success(response)
    }
  }

  class DummyListener(val port: Int, val relativePath: String) extends NodeScala.Listener {
    self =>

    @volatile private var started = false
    var handler: Exchange => Unit = null

    def createContext(h: Exchange => Unit) = this.synchronized {
      assert(started, "is server started?")
      handler = h
    }

    def removeContext() = this.synchronized {
      assert(started, "is server started?")
      handler = null
    }

    def start() = self.synchronized {
      started = true
      new Subscription {
        def unsubscribe() = self.synchronized {
          started = false
        }
      }
    }

    def emit(req: Request) = {
      val exchange = new DummyExchange(req)
      if (handler != null) handler(exchange)
      exchange
    }
  }

  class DummyServer(val port: Int) extends NodeScala {
    self =>
    val listeners = mutable.Map[String, DummyListener]()

    def createListener(relativePath: String) = {
      val l = new DummyListener(port, relativePath)
      listeners(relativePath) = l
      l
    }

    def emit(relativePath: String, req: Request) = this.synchronized {
      val l = listeners(relativePath)
      l.emit(req)
    }
  }

  test("Server should serve requests") {
    val dummy = new DummyServer(8191)
    val dummySubscription = dummy.start("/testDir") {
      request => for (kv <- request.iterator) yield (kv + "\n").toString
    }

    // wait until server is really installed
    Thread.sleep(500)

    def test(req: Request) {
      val webpage = dummy.emit("/testDir", req)
      val content = Await.result(webpage.loaded.future, 1 second)
      val expected = (for (kv <- req.iterator) yield (kv + "\n").toString).mkString
      assert(content == expected, s"'$content' vs. '$expected'")
    }

    test(immutable.Map("StrangeRequest" -> List("Does it work?")))
    test(immutable.Map("StrangeRequest" -> List("It works!")))
    test(immutable.Map("WorksForThree" -> List("Always works. Trust me.")))

    dummySubscription.unsubscribe()
  }

}




