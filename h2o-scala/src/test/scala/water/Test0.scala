package water

import org.scalatest.FunSuite

/**
  * Created by vpatryshev on 11/12/16.
  */
class Test0 extends FunSuite {
  {
    // need this because -ea IS NOT ALWAYS set in intellij
    getClass.getClassLoader.setDefaultAssertionStatus(true)
  }
  
  def willDrop[T](t:T) = water.udf.UdfBase.willDrop[T](t)
}
