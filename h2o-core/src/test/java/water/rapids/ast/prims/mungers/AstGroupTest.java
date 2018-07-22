package water.rapids.ast.prims.mungers;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.Session;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValRow;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import static org.junit.Assert.*;

public class AstGroupTest extends TestUtil {

  @BeforeClass
  static public void setup() { stall_till_cloudsize(1); }

  private Frame fr = null;

  @Before
  public void beforeEach() {
    System.out.println("Before each setup");
  }

  @Test
  public void TestGroup() {

    fr = new TestFrameBuilder()
            .withName("testFrame")
            .withColNames("ColA", "ColB", "ColC")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, ard(1, 1, 1, 2, 2, 2))
            .withDataForCol(1, ard(1, 1, 2, 1, 2, 2))
            .withDataForCol(2, ar(4, 5, 6, 7, 2, 6))
            .withChunkLayout(2, 2, 2)
            .build();
    String tree = "(GB testFrame [0, 1] sum 2 \"all\" nrow 1 \"all\")";
    Val val = Rapids.exec(tree);
    System.out.println(val.toString());
    if (val instanceof ValFrame)
      fr = val.getFrame();

    TwoDimTable twoDimTable = fr.toTwoDimTable();
    assertEquals(9L, twoDimTable.get(5, 2));
    assertEquals(6L, twoDimTable.get(6, 2));
    assertEquals(7L, twoDimTable.get(7, 2));
    assertEquals(8L, twoDimTable.get(8, 2));
    System.out.println(twoDimTable.toString());

  }

  @After
  public void afterEach() {
    System.out.println("After each setup");
    H2O.STORE.clear();
  }


}
