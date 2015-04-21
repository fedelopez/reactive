package cat.pseudocodi.week2.assignment

import cat.pseudocodi.week2.assignment.TweetLength.MaxTweetLength
import org.scalatest.{FunSuite, _}

class TweetLengthSuite extends FunSuite with ShouldMatchers {

  def tweetLength(text: String): Int =
    text.codePointCount(0, text.length)

  test("tweetRemainingCharsCount with a constant signal") {
    val result = TweetLength.tweetRemainingCharsCount(Var("hello world"))
    assert(result() == MaxTweetLength - tweetLength("hello world"))

    val tooLong = "foo" * 200
    val result2 = TweetLength.tweetRemainingCharsCount(Var(tooLong))
    assert(result2() == MaxTweetLength - tweetLength(tooLong))
  }

  test("tweetRemainingCharsCount with a dynamic signal") {
    val textSignal = Var("hello world, from Scala")
    val result = TweetLength.tweetRemainingCharsCount(textSignal)
    assert(result() == MaxTweetLength - tweetLength("hello world, from Scala"))

    textSignal() = "hello world, from Scal"
    assert(result() === MaxTweetLength - tweetLength("hello world, from Scal"))
    textSignal() = "hello world, from Sca"
    assert(result() === MaxTweetLength - tweetLength("hello world, from Sca"))
  }

  test("tweetRemainingCharsCount when no text") {
    val textSignal = Var("")
    val result = TweetLength.tweetRemainingCharsCount(textSignal)
    assert(result() == MaxTweetLength)
  }

  test("tweetRemainingCharsCount with a supplementary char") {
    val result = TweetLength.tweetRemainingCharsCount(Var("foo blabla \uD83D\uDCA9 bar"))
    assert(result() == MaxTweetLength - tweetLength("foo blabla \uD83D\uDCA9 bar"))
  }

  test("colorForRemainingCharsCount with a constant signal") {
    val resultGreen1 = TweetLength.colorForRemainingCharsCount(Var(52))
    assert(resultGreen1() == "green")
    val resultGreen2 = TweetLength.colorForRemainingCharsCount(Var(15))
    assert(resultGreen2() == "green")

    val resultOrange1 = TweetLength.colorForRemainingCharsCount(Var(12))
    assert(resultOrange1() == "orange")
    val resultOrange2 = TweetLength.colorForRemainingCharsCount(Var(0))
    assert(resultOrange2() == "orange")

    val resultRed1 = TweetLength.colorForRemainingCharsCount(Var(-1))
    assert(resultRed1() == "red")
    val resultRed2 = TweetLength.colorForRemainingCharsCount(Var(-5))
    assert(resultRed2() == "red")
  }

  test("colorForRemainingCharsCount with a dynamic signal") {
    val signal = Var(52)
    val result = TweetLength.colorForRemainingCharsCount(signal)
    assert(result() == "green")
    signal() = 15
    assert(result() == "green")
    signal() = 12
    assert(result() == "orange")
    signal() = 0
    assert(result() == "orange")
    signal() = -1
    assert(result() == "red")
    signal() = -5
    assert(result() == "red")
    signal() = 52
    assert(result() == "green")
  }

}
