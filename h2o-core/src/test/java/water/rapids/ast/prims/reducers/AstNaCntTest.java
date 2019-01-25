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
public class AstNaCntTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }


  //--------------------------------------------------------------------------------------------------------------------
  // Tests
  //--------------------------------------------------------------------------------------------------------------------

  @Test public void testAstNaCnt() {
    Frame f = null;
    try {
      f = ArrayUtils.frame(ar("A", "B"), ard(1.0, Double.NaN), ard(2.0, 23.3), ard(3.0, 3.3),
              ard(Double.NaN, 3.3));
      String x = String.format("(naCnt %s)", f._key);
      Val res = Rapids.exec(x);         // make the call to count number of NAs in frame
               // get frame without any NAs
//      assertEquals(f.numRows()-fNew.numRows() ,2);  // 2 rows of NAs removed.
      double[] naCnts = res.getNums();
      double totalNacnts = 0;
      for (int index = 0; index < f.numCols(); index++) {
        totalNacnts += naCnts[index];
      }
      assertEquals((int) totalNacnts, 2);
    } finally {
      if (f != null) f.delete();
    }
  }
}
