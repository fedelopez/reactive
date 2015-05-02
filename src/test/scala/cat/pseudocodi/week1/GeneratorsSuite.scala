package cat.pseudocodi.week1

import cat.pseudocodi.week1.Generators._
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{FlatSpec, Matchers}

class GeneratorsSuite extends FlatSpec with Matchers {

  "A generator" should "generate triangles" in {
    println(triangles(20).generate)
    println(lists.generate)
    println(trees.generate)
  }

  "An exception" should "be thrown by a failing generator" in {
    a[TestFailedException] should be thrownBy {
      check(pairs(lists, lists)) {
        case (xs, ys) => (xs ::: ys).length > xs.length
      }
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
