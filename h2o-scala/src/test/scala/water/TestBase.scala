package water

import org.scalatest.{Canceled, FunSuite, Outcome}

/**
  * Base class for H2O tests
  * These tests can be aborted.
  *
  * Idea: Eugene Zhulenev, http://stackoverflow.com/questions/26001795/how-to-configure-scalatest-to-abort-a-suite-if-a-test-fails
  */
trait TestBase extends FunSuite {
  import TestBase._

  override def withFixture(test: NoArgTest): Outcome = {
    if (aborted) {
      Canceled(s"Canceled because $explanation")
    }
    else {
      super.withFixture(test)
    }
  }

  def abort(text: String = "one of the tests failed"): Unit = {
    aborted = true
    explanation = text
  }

  def abort(exception: Throwable): Unit = {
    val root: Throwable = Stream.iterate(exception)(_.getCause).dropWhile(_.getCause != null).head

    abort(root.getMessage)
  }
}

object TestBase {
  @volatile var aborted = false
  @volatile var explanation = "nothing happened"
}
