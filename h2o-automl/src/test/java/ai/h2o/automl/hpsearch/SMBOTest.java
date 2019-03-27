package ai.h2o.automl.hpsearch;

import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.*;
import static water.TestUtil.ar;

public class SMBOTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void hasNoPrior() {
  }

  @Test
  public void updatePrior() {
  }

  @Test
  public void getNextBestHyperparameters() {
  }

  @Test
  public void getBestRowByColumn() {
    Frame fr = new TestFrameBuilder()
            .withName("testFrame2")
            .withColNames("ColA", "ColB")
            .withVecTypes(Vec.T_NUM, Vec.T_CAT)
            .withDataForCol(0, ar(1,5, 3))
            .withDataForCol(1, ar("yes", "no", "no"))
            .build();
    
    String columnName = "ColA";
    SMBO smbo = new RFSMBO(true) {
    };
    
    Frame bestRow = smbo.getBestRowByColumn(fr, columnName, true);
    
    assertEquals(1, bestRow.numRows());
    assertEquals(5, bestRow.vec(0).at(0), 1e-5);
    
    bestRow.delete();
    fr.delete();
  }
}
