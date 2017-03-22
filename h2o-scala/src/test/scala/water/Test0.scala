package water

import org.junit.{Assert, Test}
import org.scalatest.FunSuite

/**
  * Useful tools for testing our Scala code via junit.
  * 
  * Created by vpatryshev on 11/12/16.
  */
class Test0 extends FunSuite {
  {
    // need this because -ea IS NOT ALWAYS set in intellij
    getClass.getClassLoader.setDefaultAssertionStatus(true)
  }
  
  def willDrop[T](t:T) = water.udf.UdfTestBase.willDrop[T](t)

  def assertEquals(expected: Double, actual: Double, ε: Double, message: String = ""): Unit = {
    val δ: Double = math.abs(expected - actual)
    assert(δ < ε, s"Expected $expected to be equal to $actual up to $ε; $message")
  }
  
  test("just making sure that this code is accepted by gradle") {
    assert(true)
  }

  @Test def testSomething(): Unit = {
    Assert.assertTrue(true)
  }
}

object Test0 extends TestUtil {
}
