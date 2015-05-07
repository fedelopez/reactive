package cat.pseudocodi.week4.suggestions

import cat.pseudocodi.week4.gui._
import org.scalatest._

import scala.collection._
import scala.swing.Reactions.Reaction
import scala.swing.event.Event

class SwingApiTest extends FunSuite {

  object swingApi extends SwingApi {

    class ValueChanged(val textField: TextField) extends Event

    object ValueChanged {
      def unapply(x: Event) = x match {
        case vc: ValueChanged => Some(vc.textField)
        case _ => None
      }
    }

    class ButtonClicked(val source: Button) extends Event

    object ButtonClicked {
      def unapply(x: Event) = x match {
        case bc: ButtonClicked => Some(bc.source)
        case _ => None
      }
    }

    class Component {
      private val subscriptions = mutable.Set[Reaction]()

      def subscribe(r: Reaction) {
        subscriptions add r
      }

      def unsubscribe(r: Reaction) {
        subscriptions remove r
      }

      def publish(e: Event) {
        for (r <- subscriptions) r(e)
      }
    }

    class TextField extends Component {
      private var _text = ""

      def text = _text

      def text_=(t: String) {
        _text = t
        publish(new ValueChanged(this))
      }
    }

    class Button extends Component {
      def click() {
        publish(new ButtonClicked(this))
      }
    }

  }

  import swingApi._

  test("SwingApi should emit text field values to the observable") {
    val textField = new swingApi.TextField
    val values = textField.textValues

    val observed = mutable.Buffer[String]()
    val sub = values subscribe {
      observed += _
    }

    // write some text now
    textField.text = "T"
    textField.text = "Tu"
    textField.text = "Tur"
    textField.text = "Turi"
    textField.text = "Turin"
    textField.text = "Turing"

    assert(observed == Seq("T", "Tu", "Tur", "Turi", "Turin", "Turing"), observed)
  }

  test("SwingApi should emit button clicks to the observable") {
    val button = new swingApi.Button
    val values = button.clicks

    val observed = mutable.Buffer[Button]()
    val sub = values subscribe {
      observed += _
    }

    //click a few times
    button.click()
    button.click()
    button.click()

    assert(observed == Seq(button, button, button), observed)
  }

  test("SwingApi should emit button clicks to the observables") {
    val button1 = new swingApi.Button
    val button2 = new swingApi.Button
    val values1 = button1.clicks
    val values2 = button2.clicks

    val observed = mutable.Buffer[Button]()

    values1 subscribe {
      observed += _
    }
    values2 subscribe {
      observed += _
    }

    //click a few times
    button1.click()
    button2.click()
    button1.click()

    assert(observed == Seq(button1, button2, button1), observed)
  }

}
