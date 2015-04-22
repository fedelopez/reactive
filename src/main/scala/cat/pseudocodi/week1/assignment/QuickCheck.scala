package cat.pseudocodi.week1.assignment

import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalacheck._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min empty heap") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }

  //If you insert any two elements into an empty heap, finding the minimum of the resulting heap should get the smallest of the two elements back
  property("min two elements") = forAll { (a: Int, b: Int) =>
    val min = Math.min(a, b)
    val h = insert(b, insert(a, empty))
    findMin(h) == min
  }

  //If you insert an element into an empty heap, then delete the minimum, the resulting heap should be empty
  property("delete min single heap") = forAll { a: Int =>
    val h = insert(a, empty)
    deleteMin(h) == empty
  }

  property("insert list") = forAll { (l: List[A]) =>
    if (l.isEmpty) true
    else {
      val res: H = fromList(l, empty)
      l.sorted == removeAll(res, List()).reverse
    }
  }

  property("gen") = forAll { (h: H) =>
    val min = if (isEmpty(h)) 0 else findMin(h)
    findMin(insert(min, h)) == min
  }

  //Given any heap, you should get a sorted sequence of elements when continually finding and deleting minima. (Hint: recursion and helper functions are your friends.)
  property("gen1") = forAll { (h: H) =>
    val all: List[A] = removeAll(h, List())
    all.reverse == all.sorted
  }

  //Finding a minimum of the melding of any two heaps should return a minimum of one or the other.
  property("meld") = forAll { (h1: H, h2: H) =>
    if (isEmpty(h1) || isEmpty(h2)) true
    else {
      val h3: H = meld(h1, h2)
      findMin(h3) == Math.min(findMin(h1), findMin(h2))
    }
  }

  property("meld with empty") = forAll { (h1: H) =>
    h1 == meld(h1, empty)
    h1 == meld(empty, h1)
    empty == meld(empty, empty)
  }

  property("meld with itself") = forAll { (h1: H) =>
    val res1 = meld(h1, h1)
    val min1 = if (isEmpty(h1)) 0 else findMin(h1)
    val min2 = if (isEmpty(res1)) 0 else findMin(res1)
    min1 == min2
  }

  //If you insert an element into an empty heap, the resulting heap should not be empty
  property("min3") = forAll { a: Int =>
    val h = insert(a, empty)
    !isEmpty(h)
  }

  def fromList(l: List[A], h: H): H = {
    if (l.isEmpty) h
    else {
      fromList(l.tail, insert(l.head, h))
    }
  }

  def removeAll(h: H, removed: List[Int]): List[Int] = {
    if (isEmpty(h)) removed
    else {
      val min = findMin(h)
      removeAll(deleteMin(h), min :: removed)
    }
  }

  lazy val genHeap: Gen[H] = genHeapFunct(empty)

  def genHeapFunct(h: H): Gen[H] = for {
    arb1 <- arbitrary[A]
    res <- oneOf(const(h), genHeapFunct(insert(arb1, h)))
  } yield res

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
