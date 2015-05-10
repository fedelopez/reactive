package cat.pseudocodi.week4.suggestions

import cat.pseudocodi.week4.gui._
import org.scalatest._
import rx.lang.scala._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }

    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._

  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable.just("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }

  test("WikipediaApi should recover with try") {
    val obs = Observable.just(0, 1, 2, 3)
    val recovered: Observable[Try[Any]] = obs.recovered
    var completed = 0

    recovered.subscribe((value: Try[Any]) => {
      assert(Success(completed) === value)
      completed = completed + 1
    }, (t: Throwable) => fail(s"stream error $t"))

    assert(completed === 4)
  }

  test("WikipediaApi should recover with fail") {
    val obs: Observable[Int] = Observable.just(0, 1, 2, 3).map((i: Int) => if (i != 3) i else i / 0)
    val recovered: Observable[Try[Int]] = obs.recovered
    var completed = 0

    recovered.subscribe((value) => {
      if (completed != 3) {
        assert(Success(completed) === value)
      } else {
        assert(value.isFailure)
      }
      completed = completed + 1
    }, (t: Throwable) => fail(s"stream error $t"))

    assert(completed === 4)
  }

  test("WikipediaApi should not timeout") {
    val obs: Observable[Int] = Observable.just(0).delay(1 second)

    val promise: Promise[Boolean] = Promise[Boolean]()

    obs.timedOut(2).subscribe((value) => {
      promise.complete(Try(true))
    }, (t: Throwable) => fail(s"stream error $t"))

    val result: Boolean = Await.result(promise.future, 2 seconds)
    assert(result)
  }

  test("WikipediaApi should timeout") {
    val obs: Observable[Long] = Observable.interval(1 second)

    var ticks = 0

    obs.timedOut(3).subscribe((value) => {
      ticks = ticks + 1
    }, (t: Throwable) => fail(s"stream error $t"))

    Observable.timer(5 seconds).toBlocking.first

    assert(ticks > 1 && ticks < 4)
  }

  test("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable.just(1, 2, 3)
    val remoteComputation = (n: Int) => Observable.just(0 to n: _*)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) { (acc, tn) =>
      tn match {
        case Success(n) => acc + n
        case Failure(t) => throw t
      }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }

}
