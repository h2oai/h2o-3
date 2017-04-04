package water

import java.lang.reflect.Method

import org.junit.{Assert, Test}
import org.scalatest.{BeforeAndAfter, FunSuite}
import water.fvec.{Vec, Frame}

/**
  * Created by vpatryshev on 11/12/16.
  */
class Test0 extends FunSuite with BeforeAndAfter {
  {
    // need this because -ea IS NOT ALWAYS set in intellij
    getClass.getClassLoader.setDefaultAssertionStatus(true)
  }
  
  def startCloud(x: Int): Unit = {
    TestUtil.stall_till_cloudsize(x)
  }

  before(Scope.enter())
  after(Scope.exit())
  
  test("just making sure that this code is accepted by gradle") {
    assert(true)
  }

  @Test def testSomething(): Unit = {
    Assert.assertTrue(true)
  }
}

object Test0 {
  def track(f: Frame) = Scope.trackFrame(f)
  def track(v: Vec) = Scope.track(v)
  def untrack(v: Vec) = Scope.untrack(v._key)

  protected def willDrop(v: Vec): Vec = {
    return Scope.track(v)
  }

  def willDrop[T](vh: T): T = {
    try {
      val vec: Method = vh.getClass.getMethod("vec")
      Scope.track(vec.invoke(vh).asInstanceOf[Vec])
    }
    catch {
      case e: Exception => {
      }
    }
    return vh
  }
}
