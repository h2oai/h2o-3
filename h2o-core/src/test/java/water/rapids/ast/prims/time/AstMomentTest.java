package water.rapids.ast.prims.time;

import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 */
public class AstMomentTest extends TestUtil {

  @BeforeClass public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test public void generalTest() {
    AstMoment am = new AstMoment();
    assertEquals(am.nargs() - 1, am.args().length);
  }

  @Test public void time0Test() {
    Scope.enter();
    try {
      Val result = Rapids.exec("(moment 1970 1 1 0 0 0 0)");
      assertTrue(result.isFrame());
      Frame fr = result.getFrame();
      assertEquals(1, fr.numCols());
      assertEquals(1, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      assertEquals(0, fr.vec(0).at8(0));
    } finally {
      Scope.exit();
    }
  }

  @Test public void badtimeTest() {
    Scope.enter();
    try {
      // Invalid time moment -- should be cast into NaN
      Val result = Rapids.exec("(moment 1970 0 0 0 0 0 0)");
      assertTrue(result.isFrame());
      Frame fr = result.getFrame();
      assertEquals(1, fr.numCols());
      assertEquals(1, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      assertTrue(Double.isNaN(fr.vec(0).at(0)));

      result = Rapids.exec("(moment 2001 2 29 0 0 0 0)");
      assertTrue(Double.isNaN(result.getFrame().vec(0).at(0)));
    } finally {
      Scope.exit();
    }
  }

  @Test public void vectimeTest() {
    Scope.enter();
    try {
      Session session = new Session();
      new TestFrameBuilder()
          .withName("$fr", session)
          .withColNames("day", "hour", "min")
          .withDataForCol(0, ard(1, 1.1, 1.2, 2, 3))
          .withDataForCol(1, ard(0, Double.NaN, 11, 13, 15))
          .withDataForCol(2, ar(0, 0, 30, 0, 0))
          .build();

      Val result = Rapids.exec("(moment 2016 12 (cols $fr 'day') 0 0 0 0)", session);
      assertTrue(result.isFrame());
      Frame fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(5, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      long t0 = (long) fr.vec(0).at(0);
      long t1 = (long) fr.vec(0).at(1);
      long t2 = (long) fr.vec(0).at(2);
      long t3 = (long) fr.vec(0).at(3);
      long t4 = (long) fr.vec(0).at(4);
      assertEquals(0, t0 - t1);
      assertEquals(0, t1 - t2);
      assertEquals(24 * 3600 * 1000, t3 - t2);
      assertEquals(24 * 3600 * 1000, t4 - t3);

      result = Rapids.exec("(moment 2016 12 1 (cols $fr 'hour') (cols $fr 'min') 0 0)", session);
      assertTrue(result.isFrame());
      fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(5, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      double d0 = fr.vec(0).at(0);
      double d1 = fr.vec(0).at(1);
      double d2 = fr.vec(0).at(2);
      double d3 = fr.vec(0).at(3);
      double d4 = fr.vec(0).at(4);
      assertTrue("d1 should have been NaN, got " + d1 + " instead", Double.isNaN(d1));
      assertEquals((11 * 60 + 30) * 60 * 1000, (long)(d2 - d0));
      assertEquals((13 * 60) * 60 * 1000, (long)(d3 - d0));
      assertEquals((15 * 60) * 60 * 1000, (long)(d4 - d0));
    } finally {
      Scope.exit();
    }
  }

  @Test public void naTest() {
    Scope.enter();
    try {
      Val result = Rapids.exec("(moment 2000 1 1 0 0 0 NaN)");
      assertTrue(result.isFrame());
      Frame fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(1, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      assertTrue(Double.isNaN(fr.vec(0).at(0)));

      Session s = new Session();
      new TestFrameBuilder()
          .withName("$year", s)
          .withColNames("year")
          .withDataForCol(0, ard(2000, 2004, 2008))
          .build();

      result = Rapids.exec("(moment $year 1 1 0 0 NaN 0)", s);
      assertTrue(result.isFrame());
      fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(3, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      assertTrue(Double.isNaN(fr.vec(0).at(0)));
      assertTrue(Double.isNaN(fr.vec(0).at(1)));
      assertTrue(Double.isNaN(fr.vec(0).at(2)));

      new TestFrameBuilder()
          .withName("$day", s)
          .withColNames("day")
          .withDataForCol(0, ard(28, 29, 30))
          .build();

      result = Rapids.exec("(moment 2001 2 $day 0 0 0 0)", s);
      assertTrue(result.isFrame());
      fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(3, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      assertTrue(!Double.isNaN(fr.vec(0).at(0)));
      assertTrue(Double.isNaN(fr.vec(0).at(1)));
      assertTrue(Double.isNaN(fr.vec(0).at(2)));

    } finally {
      Scope.exit();
    }
  }

  @Test public void testBadArguments() {
    Scope.enter();
    try {
      Session s = new Session();
      try {
        Rapids.exec("(moment 2000 1 1 0 0 0)", s);
        fail("Expected error: Wrong number of arguments");
      } catch (IllegalArgumentException ignored) {}
      try {
        Rapids.exec("(moment 2000 1 1 [0] 0 0 0)", s);
        fail("Expected error: A NumList is not allowed");
      } catch (IllegalArgumentException ignored) {}
      try {
        Rapids.exec("(moment '2000' 1 1 0 0 0 0)", s);
        fail("Expected error: A string is not allowed");
      } catch (IllegalArgumentException ignored) {}

      new TestFrameBuilder()
          .withName("$test", s)
          .withColNames("day", "month")
          .withVecTypes(Vec.T_NUM, Vec.T_CAT)
          .withDataForCol(0, ard(5, 10, 15))
          .withDataForCol(1, ar("April", "May", "June"))
          .build();
      try {
        Rapids.exec("(moment 2010 1 $test 0 0 0 0)", s);
        fail("Expected error: frame with >1 columns passed");
      } catch (IllegalArgumentException ignored) {}
      try {
        Rapids.exec("(moment 2010 (cols $test 'month') 1 0 0 0 0)", s);
        fail("Expected error: non-numeric column used");
      } catch (IllegalArgumentException ignored) {}

      new TestFrameBuilder()
          .withName("$frame0", s)
          .withColNames("a")
          .build();
      try {
        Rapids.exec("(moment 2010 1 $frame0 0 0 0 0)", s);
        fail("Expected error: 0-rows frame used");
      } catch (IllegalArgumentException ignored) {}

      new TestFrameBuilder()
          .withName("$test2", s)
          .withColNames("month")
          .withDataForCol(0, ard(1, 2))
          .build();
      try {
        Rapids.exec("(moment 2010 (cols $test2 'month') (cols $test 'day') 0 0 0 0)", s);
        fail("Expected error: Incompatible vecs: 2 rows and 3 rows");
      } catch (IllegalArgumentException ignored) {}

    } finally {
      Scope.exit();
    }
  }

  @Test public void testOneRowFrame() {
    Scope.enter();
    try {
      Session s = new Session();
      new TestFrameBuilder()
          .withName("$frame1", s)
          .withColNames("day", "hour")
          .withDataForCol(0, ar(1))
          .withDataForCol(1, ard(Double.NaN))
          .build();
      new TestFrameBuilder()
          .withName("$month", s)
          .withColNames("month")
          .withDataForCol(0, ar(2, 3))
          .build();
      Val result = Rapids.exec("(moment 2010 $month (cols $frame1 'day') 0 0 0 0)", s);
      assertTrue(result.isFrame());
      Frame fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(2, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());

      result = Rapids.exec("(moment 2010 $month 1 (cols $frame1 'hour') 0 0 0)", s);
      assertTrue(result.isFrame());
      fr = result.getFrame();
      Scope.track(fr);
      assertEquals(1, fr.numCols());
      assertEquals(2, fr.numRows());
      assertEquals(Vec.T_TIME, fr.vec(0).get_type());
      assertTrue(Double.isNaN(fr.vec(0).at(0)));
      assertTrue(Double.isNaN(fr.vec(0).at(1)));
    } finally {
      Scope.exit();
    }
  }
}
