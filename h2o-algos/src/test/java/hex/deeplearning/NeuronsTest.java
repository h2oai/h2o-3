package hex.deeplearning;

import static hex.deeplearning.Neurons.*;

import org.junit.*;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.PrettyPrint;
import java.util.Random;

public class NeuronsTest extends water.TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  @Ignore
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
  }

}
