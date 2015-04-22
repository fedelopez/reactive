package cat.pseudocodi.week2.assignment

import cat.pseudocodi.week2.assignment.Calculator.computeValues
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

  test("computeValues") {
    val values = computeValues(Map("a" -> Signal(Literal(3)), "b" -> Signal(Literal(4))))
    assert(values.size == 2)
    val aSignal: Signal[Double] = values("a")
    assert(aSignal() == 3.0)
    val bSignal: Signal[Double] = values("b")
    assert(bSignal() == 4.0)
  }

  test("computeValues with sum") {
    val signal1: Signal[Expr] = Signal(Plus(Literal(3), Literal(5)))
    val signal2: Signal[Expr] = Signal(Plus(Literal(4), Literal(3)))
    val values = computeValues(Map("a" -> signal1, "b" -> signal2))
    assert(values.size == 2)
    val aSignal: Signal[Double] = values("a")
    assert(aSignal() == 8.0)
    val bSignal: Signal[Double] = values("b")
    assert(bSignal() == 7.0)
  }

  test("computeValues dynamic with sum") {
    val signalExpr: Var[Expr] = Var(Plus(Literal(3), Literal(5)))
    val values = computeValues(Map("a" -> signalExpr))
    val aSignal = values("a")

    signalExpr() = Plus(Literal(3), Literal(8))
    assert(aSignal() == 11.0)
    signalExpr() = Minus(Literal(3), Literal(8))
    assert(aSignal() == -5.0)
  }

  test("computeValues with ref") {
    val signal1: Signal[Expr] = Signal(Plus(Literal(3), Literal(5)))
    val signal2: Signal[Expr] = Signal(Plus(Literal(4), Ref("a")))
    val values: Predef.Map[String, Signal[Double]] = computeValues(Map("a" -> signal1, "b" -> signal2))
    assert(values.size == 2)
    val aSignal: Signal[Double] = values("a")
    assert(aSignal() == 8.0)
    val bSignal: Signal[Double] = values("b")
    assert(bSignal() == 12.0)
  }

  //todo
  test("computeValues with cyclic ref should return Double.NaN") {
    val signal1: Signal[Expr] = Signal(Plus(Literal(3), Ref("a")))
    val values = computeValues(Map("a" -> signal1))
    assert(values.size == 1)
    val aSignal: Signal[Double] = values("a")
    assert(aSignal().isNaN)
  }
}
