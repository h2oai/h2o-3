package water

import org.scalatest.FunSuite
import water.fvec.Vec

/**
  * Created by vpatryshev on 11/12/16.
  */
class TestBase extends FunSuite {
  {
    // need this because -ea IS NOT ALWAYS set in intellij
    getClass().getClassLoader().setDefaultAssertionStatus(true)
  }
  
  def willDrop[T <: Vec.Holder](t:T) = TestUtil.willDrop[T](t)
}
