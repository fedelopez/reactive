package cat.pseudocodi.week2.assignment

import org.scalatest.{FunSuite, _}

class PolynomialSuite extends FunSuite with ShouldMatchers {

  test("computeDelta when all ones") {
    val a = Signal(1.0)
    val b = Signal(1.0)
    val c = Signal(1.0)
    assert(-3.0 == Polynomial.computeDelta(a, b, c)())
  }

  test("computeDelta when positive result") {
    val a = Signal(2.0)
    val b = Signal(7.0)
    val c = Signal(3.0)
    assert(25.0 == Polynomial.computeDelta(a, b, c)())
  }

  test("computeDelta with dynamic values") {
    val a = Var(2.0)
    val b = Var(7.0)
    val c = Var(3.0)
    val delta = Polynomial.computeDelta(a, b, c)
    assert(25.0 == delta())

    a() = 1.0
    assert(37.0 == delta())
  }

  test("computeSolutions") {
    val a = Var(2.0)
    val b = Var(7.0)
    val c = Var(3.0)
    val res = Polynomial.computeSolutions(a, b, c, Polynomial.computeDelta(a, b, c))
    assert(2 == res().size)
    assert(res().contains(-0.5))
    assert(res().contains(-3.0))
  }

  test("computeSolutions with dynamic values") {
    val a = Var(2.0)
    val b = Var(7.0)
    val c = Var(3.0)
    val res = Polynomial.computeSolutions(a, b, c, Polynomial.computeDelta(a, b, c))

    b() = -8.0
    assert(res().contains(3.58113883008419))
    assert(res().contains(0.41886116991581024))
  }

  test("computeSolutions when a is zero") {
    val a = Var(0.0)
    val b = Var(1.0)
    val c = Var(1.0)
    val res = Polynomial.computeSolutions(a, b, c, Polynomial.computeDelta(a, b, c))
    assert(res().head.isNaN)
  }

  test("computeSolutions should be empty") {
    val a = Var(1.0)
    val b = Var(1.0)
    val c = Var(1.0)
    val res = Polynomial.computeSolutions(a, b, c, Polynomial.computeDelta(a, b, c))
    assert(0 == res().size)
  }

  test("computeSolutions with one root only") {
    val a = Var(2.0)
    val b = Var(4.0)
    val c = Var(2.0)
    val res = Polynomial.computeSolutions(a, b, c, Polynomial.computeDelta(a, b, c))
    assert(1 == res().size)
    assert(-1.0 == res().head)
  }

}
