package cat.pseudocodi.week2.assignment

object Polynomial {

  def computeDelta(a: Signal[Double], b: Signal[Double], c: Signal[Double]): Signal[Double] = {
    Signal(Math.pow(b(), 2) - (4 * a() * c()))
  }

  def computeSolutions(a: Signal[Double], b: Signal[Double], c: Signal[Double], delta: Signal[Double]): Signal[Set[Double]] = {
    def doIt(f: (Double, Double) => Double): Double = {
      val num = f(-1 * b(), Math.sqrt(delta()))
      val denom = 2 * a()
      num / denom
    }
    def computeReturn(): Set[Double] = {
      if (a() == 0) Set(Double.NaN)
      else if (delta() < 0) Set()
      else Set(doIt(_ + _), doIt(_ - _))
    }
    Signal(computeReturn())
  }

}
