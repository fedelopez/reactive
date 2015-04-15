val f: String => String = {
  case "ping" => "pong"
}

f("ping")
//f("abc")//gives err

//partial functions
val f2: PartialFunction[String, String] = {
  case "ping" => "pong"
}
f2.isDefinedAt("ping")
f2.isDefinedAt("pong")

//given the function
val f3: PartialFunction[List[Int], String] = {
  case Nil => "one"
  case x :: y :: rest => "two"
}
//what do you expect is the result of
f3.isDefinedAt(List(1, 2, 3))

//given the function
val g: PartialFunction[List[Int], String] = {
  case Nil => "one"
  case x :: rest =>
    rest match {
      case Nil => "two"
    }
}
//what do you expect is the result of
g.isDefinedAt(List(1, 2, 3)) //true because only applies to the outer most pattern match block

List(1, 2, 3).map(_ * 2)
List(1, 2, 3).flatMap(List(_, 1))

for (x <- List(1, 2, 3)) yield x * 2

List(2, 3, 5, 1, 3, -1).sorted

val l = 2 :: List()
val l2 = 3 :: l
l2.sorted
l2.sorted == l2.reverse