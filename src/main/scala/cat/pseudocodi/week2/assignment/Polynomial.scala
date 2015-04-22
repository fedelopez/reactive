package cat.pseudocodi.week2.assignment

object Polynomial {

  def computeDelta(a: Signal[Double], b: Signal[Double], c: Signal[Double]): Signal[Double] = {
    def doIt(): Double = {
      val bVal: Double = b()
      (bVal * bVal) - (4 * a() * c())
    }
    Signal(doIt())
  }

  def computeSolutions(a: Signal[Double], b: Signal[Double], c: Signal[Double], delta: Signal[Double]): Signal[Set[Double]] = {
    def doIt(f: (Double, Double) => Double): Double = {
      val num = f(-1 * b(), Math.sqrt(computeDelta(a, b, c)()))
      val denom = 2 * a()
      num / denom
    }
    if (a() == 0) Signal(Set(Double.NaN))
    else if (computeDelta(a, b, c)() < 0) Signal(Set())
    else Signal(Set(doIt(_ + _), doIt(_ - _)))
  }


}
