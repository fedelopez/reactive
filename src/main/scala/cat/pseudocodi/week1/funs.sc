package week1

object funs {
  println("Welcome to the Scala worksheet")
  //> Welcome to the Scala worksheet

  val f: (Int => String) = List("a", "b", "c") //> f  : Int => String = List(a, b, c)
  f(2)
  //> res0: String = c

  val fun: PartialFunction[String, String] = {
    case "ping" => "pong"
  }
  //> fun  : PartialFunction[String,String] = <function1>
  fun("ping") //> res1: String = pong
  fun.isDefinedAt("ping") //> res2: Boolean = true

}