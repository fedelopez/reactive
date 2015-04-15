package cat.pseudocodi.week1

import cat.pseudocodi.week1.Generators._
import org.scalatest.FunSuite

class GeneratorsSuite extends FunSuite {

  test("generators") {
    println(triangles(20).generate)
    println(lists.generate)
    println(trees.generate)
  }

  test("example of a failing generator") {
    check(pairs(lists, lists)) {
      case (xs, ys) => (xs ::: ys).length > xs.length
    }
  }

  def check[T](r: Generator[T], noTimes: Int = 100)(test: T => Boolean) {
    for (_ <- 0 until noTimes) {
      val value = r.generate
      assert(test(value), "Test failed for: " + value)
    }
    println("Test passed " + noTimes + " times")
  }


}
