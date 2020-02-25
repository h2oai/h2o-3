package hex.psvm.psvm;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.VecUtils;

import java.util.Arrays;

import static org.junit.Assert.*;
import static water.TestUtil.ar;

public class MatrixUtilsTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void productMtDM() {
    try {
      Scope.enter();
      Frame h = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1.0, 2.0))
              .withDataForCol(1, ard(0.0, 3.0))
              .withDataForCol(2, ard(4.0, 5.0))
              .build();
      Scope.track(h);
      Vec d = h.remove(2);

      LLMatrix m = MatrixUtils.productMtDM(h, d);

      assertEquals(24, m.get(0, 0), 0);
      assertEquals(30, m.get(1, 0), 0);
      assertEquals(45, m.get(1, 1), 0);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void subtractionMtv() {
    try {
      Scope.enter();
      Frame m = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1.0, 1.0, 1.0))
              .withDataForCol(1, ard(1.0, 1.0, 1.0))
              .build();
      Scope.track(m);
      Vec v = Vec.makeVec(ard(1.0, 1.0), Vec.newKey());
      
      Frame res = MatrixUtils.subtractionMtv(m, v);

      // check result
      Assert.assertEquals(2, res.vecs().length);
      for (Vec vec : res.vecs()) {
        Assert.assertTrue(vec.isConst());
        Assert.assertEquals(0, vec.min(),0);
      }

      // check given matrix
      Assert.assertEquals(2, m.vecs().length);
      for (Vec vec : m.vecs()) {
        Assert.assertTrue(vec.isConst());
        Assert.assertEquals(1, vec.min(),0);
      }

      // check given vector
      Assert.assertTrue(v.isConst());
      Assert.assertEquals(1, v.min(),0);
      
    } finally {
      Scope.exit();
    }
  }  
  
  @Test
  public void productMtv2() {
    try {
      Scope.enter();
      Frame m = new TestFrameBuilder()
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1.0, 1.0, 1.0, 1.0))
              .withDataForCol(1, ard(1.0, 1.0, 1.0, 1.0))
              .build();
      Scope.track(m);
      Vec v = Vec.makeVec(ard(2.0, 2.0), Vec.newKey());

      Vec resVec = MatrixUtils.productMtv2(m, v);

      // check result
      Assert.assertEquals(m.numRows(), resVec.length());
      Assert.assertEquals(4, resVec.at(0),0);
      Assert.assertEquals(4, resVec.at(1),0);
      Assert.assertEquals(4, resVec.at(2),0);
      Assert.assertEquals(4, resVec.at(3),0);

      // check given matrix
      Assert.assertEquals(2, m.vecs().length);
      for (Vec vec : m.vecs()) {
        Assert.assertTrue(vec.isConst());
        Assert.assertEquals(1, vec.min(),0);
      } 

      // check given vector
      Assert.assertTrue(v.isConst());
      Assert.assertEquals(2, v.min(),0);

    } finally {
      Scope.exit();
    }
  }

    @Test
    public void productMtv22() {
        try {
            Scope.enter();
            Frame m = Scope.track(parse_test_file("smalldata/anomaly/const.csv"));
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, 2.0), Vec.newKey());

            Vec resVec = MatrixUtils.productMtv2(m, v);

            // check result
            Assert.assertEquals(m.numRows(), resVec.length());
            Assert.assertEquals(4, resVec.at(0),0);
            Assert.assertEquals(4, resVec.at(1),0);
            Assert.assertEquals(4, resVec.at(2),0);
            Assert.assertEquals(4, resVec.at(3),0);

            // check given matrix
            Assert.assertEquals(2, m.vecs().length);
            for (Vec vec : m.vecs()) {
                Assert.assertTrue(vec.isConst());
                Assert.assertEquals(1, vec.min(),0);
            }

            // check given vector
            Assert.assertTrue(v.isConst());
            Assert.assertEquals(2, v.min(),0);

        } finally {
            Scope.exit();
        }
    }  

}
