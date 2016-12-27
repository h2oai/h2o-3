package water.rapids.ast.prims.reducers;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;

import static org.junit.Assert.assertEquals;


/**
 * Test the AstNaCnt.java class
 */
public class AstNaRowCntTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }


  //--------------------------------------------------------------------------------------------------------------------
  // Tests
  //--------------------------------------------------------------------------------------------------------------------

  @Test public void testAstRowNaCnt1() {
    Frame f = null;
    try {
      f = ArrayUtils.frame(ar("A", "B"), ard(1.0, Double.NaN), ard(2.0, 23.3), ard(3.0, 3.3),
              ard(Double.NaN, 3.3));
         // make the call to count number of NAs rows in frame
      String x = String.format("(naRowCnt %s)", f._key);
      Val res = Rapids.exec(x);         // make the call to count number of NAs rows  in frame
      assertEquals((int) res.getNum(), 2);
    } finally {
      if (f != null) f.delete();
    }
  }

  @Test public void testAstRowNaCnt2() {
    Frame f = null;
    try {
      f = ArrayUtils.frame(ar("A", "B"), ard(1.0, Double.NaN), ard(2.0, 23.3), ard(3.0, 3.3),
              ard(Double.NaN, 3.3), ard(Double.NaN, Double.NaN));
      String x = String.format("(naRowCnt %s)", f._key);
      Val res = Rapids.exec(x);         // make the call to count number of NAs rows  in frame
      assertEquals((int) res.getNum(), 3);
    } finally {
      if (f != null) f.delete();
    }
  }
}