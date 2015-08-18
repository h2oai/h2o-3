package hex.deeplearning;

import static hex.deeplearning.Neurons.*;

import org.junit.*;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PrettyPrint;
import java.util.Random;

public class NeuronsTest extends water.TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void matrixVecTest() {
    int rows = 2048;
    int cols = 8192;
    int loops = 5;
    int warmup_loops = 5;
    long seed = 0x533D;
    float nnz_ratio_vec = 0.01f; //fraction of non-zeroes for vector
    float nnz_ratio_mat = 0.1f; //fraction of non-zeroes for matrix

    float [] a = new float[rows*cols];
    double [] x = new double[cols];
    double [] y = new double[rows];
    double [] res = new double[rows];
    byte [] bits = new byte[rows];

    for (int row=0;row<rows;++row) {
      y[row] = 0;
      res[row] = 0;
      bits[row] = (byte)("abcdefghijklmnopqrstuvwxyz".toCharArray()[row%26]);
    }
    Random rng = new Random(seed);
    for (int col=0;col<cols;++col)
      if (rng.nextFloat() < nnz_ratio_vec)
        x[col] = ((float)col)/cols;

    for (int row=0;row<rows;++row) {
      int off = row*cols;
      for (int col=0;col<cols;++col) {
        if (rng.nextFloat() < nnz_ratio_mat)
          a[off+col] = ((float)(row+col))/cols;
      }
    }
    Storage.DenseRowMatrix dra = new Storage.DenseRowMatrix(a, rows, cols);
    Storage.DenseColMatrix dca = new Storage.DenseColMatrix(dra, rows, cols);
    Storage.SparseRowMatrix sra = new Storage.SparseRowMatrix(dra, rows, cols);
    Storage.SparseColMatrix sca = new Storage.SparseColMatrix(dca, rows, cols);
    Storage.DenseVector dx = new Storage.DenseVector(x);
    Storage.DenseVector dy = new Storage.DenseVector(y);
    Storage.DenseVector dres = new Storage.DenseVector(res);
    Storage.SparseVector sx = new Storage.SparseVector(x);

    /**
     * warmup
     */
    System.out.println("warming up.");
    float sum = 0;
    for (int l=0;l<warmup_loops;++l) {
      gemv_naive(res, a, x, y, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv_naive(dres, dra, dx, dy, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv_row_optimized(res, a, x, y, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv(dres, dca, dx, dy, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv(dres, dra, sx, dy, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv(dres, dca, sx, dy, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv(dres, sra, sx, dy, bits);
      sum += res[rows/2];
    }
    for (int l=0;l<warmup_loops;++l) {
      gemv(dres, sca, sx, dy, bits);
      sum += res[rows/2];
    }

    /**
     * naive version
     */
    System.out.println("\nstarting naive.");
    sum = 0;
    long start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv_naive(res, a, x, y, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("naive time: " + PrettyPrint.msecs(System.currentTimeMillis() - start, true));



    System.out.println("\nstarting dense row * dense.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv_naive(dres, dra, dx, dy, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("dense row * dense time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));



    System.out.println("\nstarting optimized dense row * dense.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv_row_optimized(res, a, x, y, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("optimized dense row * dense time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));



    System.out.println("\nstarting dense col * dense.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv(dres, dca, dx, dy, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("dense col * dense time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));



    System.out.println("\nstarting dense row * sparse.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv(dres, dra, sx, dy, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("dense row * sparse time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));



    System.out.println("\nstarting dense col * sparse.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv(dres, dca, sx, dy, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("dense col * sparse time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));



    System.out.println("\nstarting sparse row * sparse.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv(dres, sra, sx, dy, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("sparse row * sparse time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));



    System.out.println("\nstarting sparse col * sparse.");
    sum = 0;
    start = System.currentTimeMillis();
    for (int l=0;l<loops;++l) {
      gemv(dres, sca, sx, dy, bits);
      sum += res[rows/2]; //do something useful
    }
    System.out.println("result: " + sum + " and " + ArrayUtils.sum(res));
    System.out.println("sparse col * sparse time: " + PrettyPrint.msecs(System.currentTimeMillis()-start, true));
  }

  @Test
  public void sparseTester() {
    Storage.DenseVector dv = new Storage.DenseVector(20);
    dv.set(3,0.21f);
    dv.set(7,0.13f);
    dv.set(18,0.14f);
    Storage.SparseVector sv = new Storage.SparseVector(dv);
    assert(sv.size() == 20);
    assert(sv.nnz() == 3);

    // dense treatment
    for (int i=0;i<sv.size();++i)
      Log.info("sparse [" + i + "] = " + sv.get(i));

    // sparse treatment
    for (Storage.SparseVector.Iterator it=sv.begin(); !it.equals(sv.end()); it.next()) {
//      Log.info(it.toString());
      Log.info(it.index() + " -> " + it.value());
    }

    Storage.DenseColMatrix dcm = new Storage.DenseColMatrix(3,5);
    dcm.set(2,1,3.2f);
    dcm.set(1,3,-1.2f);
    assert(dcm.get(2,1)==3.2f);
    assert(dcm.get(1,3)==-1.2f);
    assert(dcm.get(0,0)==0f);

    Storage.DenseRowMatrix drm = new Storage.DenseRowMatrix(3,5);
    drm.set(2,1,3.2f);
    drm.set(1,3,-1.2f);
    assert(drm.get(2,1)==3.2f);
    assert(drm.get(1,3)==-1.2f);
    assert(drm.get(0,0)==0f);

    Storage.SparseColMatrix scm = new Storage.SparseColMatrix(3,5);
    scm.set(2,1,3.2f);
    scm.set(1,3,-1.2f);
    assert(scm.get(2,1)==3.2f);
    assert(scm.get(1,3)==-1.2f);
    assert(scm.get(0,0)==0f);

    Storage.SparseRowMatrix srm = new Storage.SparseRowMatrix(3,5);
    srm.set(2,1,3.2f);
    srm.set(1,3,-1.2f);
    assert(srm.get(2,1)==3.2f);
    assert(srm.get(1,3)==-1.2f);
    assert(srm.get(0,0)==0f);
  }
}
