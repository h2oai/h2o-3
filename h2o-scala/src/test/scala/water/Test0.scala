package water

import org.junit.{Assert, Test}
import org.scalatest.FunSuite

/**
  * Created by vpatryshev on 11/12/16.
  */
class Test0 extends FunSuite {
  {
    // need this because -ea IS NOT ALWAYS set in intellij
    getClass.getClassLoader.setDefaultAssertionStatus(true)
  }
  
  def willDrop[T](t:T) = water.udf.UdfTestBase.willDrop[T](t)
  
  test("just making sure that this code is accepted by gradle") {
    assert(true)
  }

  @Test def testSomething(): Unit = {
    Assert.assertTrue(true)
  }
}
