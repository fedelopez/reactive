package cat.pseudocodi.week2.assignment

import org.scalatest.{FunSuite, _}

import scala.collection.immutable.Map

class CalculatorSuite extends FunSuite with ShouldMatchers {

  test("eval literal") {
    val eval: Double = Calculator.eval(Literal(3), Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(eval == 3)
  }

  test("eval plus") {
    val eval: Double = Calculator.eval(Plus(Literal(3), Literal(5)), Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(eval == 8)
  }

  test("eval minus") {
    val eval: Double = Calculator.eval(Minus(Literal(10), Literal(3)), Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(eval == 7)
  }

  test("eval times") {
    val eval: Double = Calculator.eval(Times(Literal(10), Literal(3)), Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(eval == 30)
  }

  test("eval divide") {
    val eval: Double = Calculator.eval(Divide(Literal(9), Literal(3)), Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(eval == 3)
  }

  test("eval ref") {
    val eval: Double = Calculator.eval(Ref("b"), Map("a" -> Signal(Literal(3)), "b" -> Signal(Plus(Ref("a"), Literal(4)))))
    assert(eval == 7)
  }

  test("computeValues") {
    val values: Predef.Map[String, Signal[Double]] = Calculator.computeValues(Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(values.size == 2)
    val aSignal: Signal[Double] = values("a")
    assert(aSignal() == 3.0)
    val bSignal: Signal[Double] = values("b")
    assert(bSignal() == 4.0)
  }


}
