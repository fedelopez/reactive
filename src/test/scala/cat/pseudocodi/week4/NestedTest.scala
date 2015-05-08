package cat.pseudocodi.week4

import org.scalatest._
import rx.lang.scala._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author fede
 */
class NestedTest extends FunSuite {

  test("flattenNestedStreams") {
    val xs: Observable[Int] = Observable.just(3, 2, 1)
    val yss: Observable[Observable[Int]] = xs.map(x => Observable.interval(x seconds).map(_ => x).take(2))
    val zs = yss.flatten

    val list = zs.toBlocking.toList
    val isFirst = list == List(1, 1, 2, 3, 2, 3)
    val isSecond = list == List(1, 2, 1, 3, 2, 3)
    // behavior of flatten is non-deterministic
    assert(isFirst || isSecond)
    if (isFirst) println("first option")
    if (isSecond) println("second option")
  }

  test("concatenateNestedStreams") {
    val xs: Observable[Int] = Observable.just(3, 2, 1)
    val yss: Observable[Observable[Int]] = xs.map(x => Observable.interval(x seconds).map(_ => x).take(2))
    val zs = yss.concat

    assert(List(3, 3, 2, 2, 1, 1) === zs.toBlocking.toList)
  }
}
