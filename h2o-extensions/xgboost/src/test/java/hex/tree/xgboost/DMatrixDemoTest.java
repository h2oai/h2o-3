package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DMatrixDemoTest {

  @Test // shows how to convert a small (=fits in a single java array) to DMatrix using our "2D" API
  public void convertSmallUnitMatrix2DAPI() throws XGBoostError {
    DMatrix dMatrix = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);

      final int N = 3;

      long[][] rowHeaders = new long[1][N + 1]; // Unit matrix, one non-zero element per row
      int[][] colIndices = new int[1][N];
      float[][] values = new float[1][N];

      int pos = 0;
      for (int m = 0; m < N; m++) {
        for (int n = 0; n < N; n++) {
          if (m == n) {
            values[0][pos] = 1;
            colIndices[0][pos] = m;
            rowHeaders[0][pos] = pos;
            pos++;
          }
        }
      }
      rowHeaders[0][pos] = pos;
      assertEquals(N, pos);

      System.out.println("Headers: " + Arrays.toString(rowHeaders[0]));
      System.out.println("Col idx: " + Arrays.toString(colIndices[0]));
      System.out.println("Values : " + Arrays.toString(values[0]));

      // this can be confusing:
      final int shapeParam = N; // number of columns (note: can also be set to 0; 0 means guess from data)
      final int shapeParam2 = N + 1; // number of rows in the matrix + 1
      final long ndata = N; // total number of values

      dMatrix = new DMatrix(rowHeaders, colIndices, values, DMatrix.SparseType.CSR, shapeParam, shapeParam2, ndata);
      assertEquals(3, dMatrix.rowNum());
    } finally {
      if (dMatrix != null) {
        dMatrix.dispose();
      }
      Rabit.shutdown();
    }
  }


}
