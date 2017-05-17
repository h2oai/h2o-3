package hex.DTMatrix;

/**
 * Created by wendycwong on 1/6/17.
 */


import hex.DMatrix;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.VecUtils;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import static java.lang.Math.abs;

public class DMatrixTest extends TestUtil {
    public static final double TOLERANCE = 1e-6;
    @BeforeClass public static void setup() { stall_till_cloudsize(1); }


    /*
    Test multiply function of DMatrix.  Basically, if you call DMatrix.mmul(A, B), it will return
    A*B back to you as a frame.  This is exactly what I need.  Let's see if it does categorical
    data columns.
     */
    @Test public void testMatrixMultiply() throws InterruptedException, ExecutionException {
        Frame train = null, tTrain = null, productF = null;
        try {
            train = ArrayUtils.frame(ar("A", "B"), ard(1.0, 2), ard(3.0, 4.3), ard(5,6)); //one row per parenthesis
            tTrain = ArrayUtils.frame(ar("A", "B","C"), ard(4,2,5), ard(3,1,6));
            double[][] answer = ard(ard(10, 4, 17), ard(24.9, 10.3, 40.8), ard(38, 16, 61));
            // transpose the train matrix
            productF = DMatrix.mmul(train, tTrain);

            //check product matrix dimension is correct
            Assert.assertEquals(train.numRows(), productF.numRows());
            Assert.assertEquals(tTrain.numCols(), productF.numCols());

            // check some elements
            Assert.assertTrue(abs(productF.vec(0).at(0)-answer[0][0]) < 1e-10);
            Assert.assertTrue(abs(productF.vec(1).at(0)-answer[0][1]) < 1e-10);

        } finally {
            if (train != null) train.delete();
            if (tTrain != null) tTrain.delete();
            if (productF != null) productF.delete();
        }
    }


    /*
  This unit test uses a small datasets, calculate the eigenvectors/values with original PCA implementation.
  Next, it calculates the eigenvectors/values using PCA with wide dataset flag set to true.  Then, we
  compare the eigenvalues/vectors from both methods and they should agree.  Dataset contains numerical and
  categorical columns.
   */
    @Test public void testMatrixTransposeNA() throws InterruptedException, ExecutionException {
        Frame train = null, tTrain = null;
        try {
            train = parse_test_file(Key.make("prostate_cat.hex"), "smalldata/pca_test/decathlon.csv");
            train.remove(12).remove();    // remove categorical columns
            train.remove(11).remove();
            train.remove(10).remove();
            // transpose the train matrix
            tTrain = DMatrix.transpose(train);  // a brand new frame tTrain is created

            //check transpose matrix dimenstion is correct
            Assert.assertEquals(train.numCols(), tTrain.numRows());
            Assert.assertEquals(train.numRows(), tTrain.numCols());

            // check some elements
            Assert.assertEquals(train.vec(2).at(8),tTrain.vec(8).at(2),1e-10);
            Assert.assertTrue(abs(train.vec(4).at(18)-tTrain.vec(18).at(4)) < 1e-10);

        } finally {
            if (train != null) train.delete();
            if (tTrain != null) tTrain.delete();
        }
    }

    @Test public void testMatrixTransposeSparse() throws InterruptedException, ExecutionException {
        Frame train = null, tTrain = null;
        try {
            double [] vals1 = new double[1024]; // sparse
            double [] vals2 = new double[1024]; // dense
            double [] vals3 = new double[1024]; // sparse
            double [] vals4 = new double[1024]; // dense
            Random rnd = new Random();
            for(int i = 0; i < 96; i++){
                vals1[rnd.nextInt(vals1.length)] = rnd.nextInt(100);
                vals3[rnd.nextInt(vals3.length)] = rnd.nextFloat();
            }
            for(int i = 0; i < vals2.length; ++i) {
                vals2[i] = rnd.nextDouble();
                vals4[i] = rnd.nextInt();
            }
            Vec.VectorGroup vg = Vec.VectorGroup.VG_LEN1;
            Vec v1 = Vec.makeCon(vg.addVec(),vals1);
            Vec v2 = Vec.makeCon(vg.addVec(),vals2);
            Vec v3 = Vec.makeCon(vg.addVec(),vals3);
            Vec v4 = Vec.makeCon(vg.addVec(),vals4);
            train = new Frame(new Vec[]{v1,v2,v3,v4});
            tTrain = DMatrix.transpose(train);
            Assert.assertEquals(vals1.length,tTrain.numCols());
            for(int i = 0; i < vals1.length; ++i){
                Vec x = tTrain.vec(i);
                Assert.assertEquals(vals1[i],x.at(0),0);
                Assert.assertEquals(vals2[i],x.at(1),0);
                Assert.assertEquals(vals3[i],x.at(2),0);
                Assert.assertEquals(vals4[i],x.at(3),0);
            }
        } finally {
            if (train != null) train.delete();
            if (tTrain != null) tTrain.delete();
        }
    }
}