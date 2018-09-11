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

import static org.junit.Assert.assertEquals;

public class AstIsNaTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Test
  public void IsNaTest() {
    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_STR)
            .withDataForCol(0, ard(1, Double.NaN))
            .withDataForCol(1, ar("1", null))
            .build();
    String tree = "(is.na (cols testFrame [0.0] ) )";
    Val val = Rapids.exec(tree);
    Frame results = val.getFrame();

    assertEquals(0.0, results.vec(0).at(0), 1e-5);
    assertEquals(1.0, results.vec(0).at(1), 1e-5);

    String tree2 = "(is.na (cols testFrame [1.0] ) )";
    Val val2 = Rapids.exec(tree2);
    Frame results2 = val2.getFrame();

    assertEquals(0.0, results2.vec(0).at(0), 1e-5);
    assertEquals(1.0, results2.vec(0).at(1), 1e-5);

    results.delete();
    results2.delete();
  }

  @After
  public void afterEach() {
    fr.delete();
  }


}
