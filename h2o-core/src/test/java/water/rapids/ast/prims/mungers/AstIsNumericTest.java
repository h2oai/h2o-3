package water.rapids.ast.prims.mungers;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.H2O;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.util.TwoDimTable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AstIsNumericTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void IsNumericTest() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, 1))
            .withDataForCol(1, ar("1", "2"))
            .build();
    String tree = "(is.numeric (cols testFrame [0.0] ) )";
    Val val = Rapids.exec(tree);
    double[] results = val.getNums();

    assertEquals(1.0, results[0], 1e-5);

    String tree2 = "(is.numeric (cols testFrame [1.0] ) )";
    Val val2 = Rapids.exec(tree2);
    double[] results2 = val2.getNums();

    assertEquals(0.0, results2[0], 1e-5);
  }

  @After
  public void afterEach() {
    fr.delete();
  }


}
