package hex.psvm.psvm;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

import static org.junit.Assert.assertEquals;

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
                Assert.assertEquals(0, vec.min(), 0);
            }

            // check given matrix
            Assert.assertEquals(2, m.vecs().length);
            for (Vec vec : m.vecs()) {
                Assert.assertTrue(vec.isConst());
                Assert.assertEquals(1, vec.min(), 0);
            }

            // check given vector
            Assert.assertTrue(v.isConst());
            Assert.assertEquals(1, v.min(), 0);

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void subtractionMtvBigData() {
        try {
            Scope.enter();
            Frame m = Scope.track(generate_real_only(4, 16384, 0, 0xCAFFE));
            Scope.track(m);
            Vec v = Vec.makeVec(ard(1.0, 1.0, 1.0, 1.0), Vec.newKey());

            Frame res = MatrixUtils.subtractionMtv(m, v);

            // check result
            Assert.assertEquals(m.numRows(), res.numRows());
            Assert.assertEquals(31.691, res.vec(0).at(0), 1e-3);
            Assert.assertEquals(-57.398, res.vec(0).at(1024), 1e-3);

            // check given matrix
            Assert.assertEquals(4, m.vecs().length);
            Assert.assertEquals(32.691, m.vec(0).at(0), 1e-3);

            // check given vector
            Assert.assertTrue(v.isConst());
            Assert.assertEquals(1, v.min(), 0);

        } finally {
            Scope.exit();
        }
    }

    @Test
    public void subtractionMtvArray() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(1.0, 1.0, 1.0))
                    .withDataForCol(1, ard(1.0, 1.0, 1.0))
                    .build();
            Scope.track(m);
            double[] array = ard(1.0, 1.0);

            Frame res = MatrixUtils.subtractionMtArray(m, array);

            // check result
            Assert.assertEquals(2, res.vecs().length);
            for (Vec vec : res.vecs()) {
                Assert.assertTrue(vec.isConst());
                Assert.assertEquals(0, vec.min(), 0);
            }

            // check given matrix
            Assert.assertEquals(2, m.vecs().length);
            for (Vec vec : m.vecs()) {
                Assert.assertTrue(vec.isConst());
                Assert.assertEquals(1, vec.min(), 0);
            }

            // check given array
            Assert.assertEquals(1, array[0], 0);
            Assert.assertEquals(1, array[1], 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void subtractionMtvArrayBigData() {
        try {
            Scope.enter();
            Frame m = Scope.track(generate_real_only(4, 16384, 0, 0xCAFFE));
            Scope.track(m);
            double[] array = ard(1.0, 1.0, 1.0, 1.0);

            Frame res = MatrixUtils.subtractionMtArray(m, array);

            // check result
            Assert.assertEquals(m.numRows(), res.numRows());
            Assert.assertEquals(31.691, res.vec(0).at(0), 1e-3);
            Assert.assertEquals(-57.398, res.vec(0).at(1024), 1e-3);

            // check given matrix
            Assert.assertEquals(4, m.vecs().length);
            Assert.assertEquals(32.691, m.vec(0).at(0), 1e-3);

            // check given array
            Assert.assertEquals(1, array[0], 0);
            Assert.assertEquals(1, array[1], 0);
            Assert.assertEquals(1, array[2], 0);
            Assert.assertEquals(1, array[3], 0);
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
            Assert.assertEquals(2, m.vecs().length);
            Assert.assertEquals(1.0, m.vec(0).at(0), 1e-3);

            // check given matrix
            Assert.assertEquals(2, m.vecs().length);
            for (Vec vec : m.vecs()) {
                Assert.assertTrue(vec.isConst());
                Assert.assertEquals(1, vec.min(), 0);
            }

            // check given vector
            Assert.assertTrue(v.isConst());
            Assert.assertEquals(2, v.min(), 0);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Big data equals 16384
     */
    @Test
    public void productMtv2BigData() {
        try {
            Scope.enter();
            Frame m = Scope.track(generate_real_only(4, 16384, 0, 0xCAFFE));
            Scope.track(m);
            Vec v = Vec.makeVec(ard(2.0, 2.0, 2.0, 2.0), Vec.newKey());

            Vec resVec = MatrixUtils.productMtv2(m, v);

            // check result
            Assert.assertEquals(m.numRows(), resVec.length());
            Assert.assertEquals(242.913, resVec.at(0), 1e-3);
            Assert.assertEquals(-506.966, resVec.at(1024), 1e-3);

            // check given matrix
            Assert.assertEquals(4, m.vecs().length);
            Assert.assertEquals(32.691, m.vec(0).at(0), 1e-3);

            // check given vector
            Assert.assertTrue(v.isConst());
            Assert.assertEquals(2, v.min(), 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void productMtv2Array() {
        try {
            Scope.enter();
            Frame m = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM)
                    .withDataForCol(0, ard(1.0, 1.0, 1.0, 1.0))
                    .withDataForCol(1, ard(1.0, 1.0, 1.0, 1.0))
                    .build();
            Scope.track(m);
            double[] array = new double[]{2.0, 2.0};

            Vec resVec = MatrixUtils.productMtv2Array(m, array);

            // check result
            Assert.assertEquals(m.numRows(), resVec.length());
            Assert.assertEquals(4, resVec.at(0), 0);
            Assert.assertEquals(4, resVec.at(1), 0);
            Assert.assertEquals(4, resVec.at(2), 0);
            Assert.assertEquals(4, resVec.at(3), 0);

            // check given matrix
            Assert.assertEquals(2, m.vecs().length);
            for (Vec vec : m.vecs()) {
                Assert.assertTrue(vec.isConst());
                Assert.assertEquals(1, vec.min(), 0);
            }

            // check given array
            Assert.assertEquals(2, array[0], 0);
            Assert.assertEquals(2, array[1], 0);
        } finally {
            Scope.exit();
        }
    }

    /**
     * Big data equals 16384
     */
    @Test
    public void productMtv2BigDataArray() {
        try {
            Scope.enter();
            Frame m = Scope.track(generate_real_only(4, 16384, 0, 0xCAFFE));
            Scope.track(m);
            double[] array = new double[]{2.0, 2.0, 2.0, 2.0};

            Vec resVec = MatrixUtils.productMtv2Array(m, array);

            // check result
            Assert.assertEquals(m.numRows(), resVec.length());
            Assert.assertEquals(242.913, resVec.at(0), 1e-3);
            Assert.assertEquals(-506.966, resVec.at(1024), 1e-3);

            // check given matrix
            Assert.assertEquals(4, m.vecs().length);
            Assert.assertEquals(32.691, m.vec(0).at(0), 1e-3);

            // check given array
            Assert.assertEquals(2, array[0], 0);
            Assert.assertEquals(2, array[1], 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void productVtv() {
        try {
            Scope.enter();
            Vec v1 = Vec.makeVec(ard(2.0, 2.0, 2.0, 2.0), Vec.newKey());
            Vec v2 = Vec.makeVec(ard(2.0, 2.0, 2.0, 2.0), Vec.newKey());

            double res = MatrixUtils.productVtV(v1, v2);

            // check result
            Assert.assertEquals(16, res, 0);

            // check given vector
            Assert.assertTrue(v1.isConst());
            Assert.assertEquals(2, v1.min(), 0);

            // check given vector
            Assert.assertTrue(v2.isConst());
            Assert.assertEquals(2, v2.min(), 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void productVtvBigData() {
        try {
            Scope.enter();
            Frame m = Scope.track(generate_real_only(2, 16384, 0, 0xCAFFE));
            Vec v1 = m.vec(0);
            Vec v2 = m.vec(1);

            double res = MatrixUtils.productVtV(v1, v2);

            // check result
            Assert.assertEquals(529463.066, res, 1e-3);

            // check given vector
            Assert.assertEquals(32.691, v1.at(0), 1e-3);
            Assert.assertEquals(-91.936, v1.at(1024), 1e-3);

            // check given vector
            Assert.assertEquals(34.599, v2.at(0), 1e-3);
            Assert.assertEquals(55.136, v2.at(1024), 1e-3);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void subtractionVtv() {
        try {
            Scope.enter();
            Vec v1 = Vec.makeVec(ard(2.0, 2.0, 2.0, 2.0), Vec.newKey());
            Vec v2 = Vec.makeVec(ard(2.0, 2.0, 2.0, 2.0), Vec.newKey());

            Vec res = MatrixUtils.subtractionVtv(v1, v2);

            // check result
            Assert.assertTrue(res.isConst());
            Assert.assertEquals(0, res.min(), 0);

            // check given vector
            Assert.assertTrue(v1.isConst());
            Assert.assertEquals(2, v1.min(), 0);

            // check given vector
            Assert.assertTrue(v2.isConst());
            Assert.assertEquals(2, v2.min(), 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void subtractionVtvBigData() {
        try {
            Scope.enter();
            Frame m = Scope.track(generate_real_only(2, 16384, 0, 0xCAFFE));
            Vec v1 = m.vec(0);
            Vec v2 = m.vec(1);

            Vec res = MatrixUtils.subtractionVtv(v1, v2);
            // check result
            Assert.assertEquals(-1.908, res.at(0), 1e-3);

            // check given vector
            Assert.assertEquals(32.691, v1.at(0), 1e-3);
            Assert.assertEquals(-91.936, v1.at(1024), 1e-3);

            // check given vector
            Assert.assertEquals(34.599, v2.at(0), 1e-3);
            Assert.assertEquals(55.136, v2.at(1024), 1e-3);
        } finally {
            Scope.exit();
        }
    }    

}
