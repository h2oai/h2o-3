package hex.tree.xgboost;

import ml.dmlc.xgboost4j.java.DMatrix;
import ml.dmlc.xgboost4j.java.Rabit;
import ml.dmlc.xgboost4j.java.XGBoostError;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.TestBase;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DMatrixDemoTest extends TestBase {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Test // shows how to convert a small (=fits in a single java array) to DMatrix using our "2D" API
  public void convertSmallUnitMatrix2DAPI() throws XGBoostError {
    DMatrix dMatrix = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);

      dMatrix = makeSmallUnitMatrix(3);

      assertEquals(3, dMatrix.rowNum());
    } finally {
      if (dMatrix != null) {
        dMatrix.dispose();
      }
      Rabit.shutdown();
    }
  }

  private static DMatrix makeSmallUnitMatrix(final int N) throws XGBoostError {
    long[][] rowHeaders = new long[1][N + 1]; // Unit matrix, one non-zero element per row
    int[][] colIndices = new int[1][N];
    float[][] values = new float[1][N];

    int pos = 0;
    for (int m = 0; m < N; m++) {
      values[0][pos] = 1;
      colIndices[0][pos] = m;
      rowHeaders[0][pos] = pos;
      pos++;
    }
    rowHeaders[0][pos] = pos;
    assertEquals(N, pos);

    if (N < 10) {
      System.out.println("Headers: " + Arrays.toString(rowHeaders[0]));
      System.out.println("Col idx: " + Arrays.toString(colIndices[0]));
      System.out.println("Values : " + Arrays.toString(values[0]));
    }

    // this can be confusing:
    final int shapeParam = N; // number of columns (note: can also be set to 0; 0 means guess from data)
    final int shapeParam2 = N + 1; // number of rows in the matrix + 1
    final long ndata = N; // total number of values

    return new DMatrix(rowHeaders, colIndices, values, DMatrix.SparseType.CSR, shapeParam, shapeParam2, ndata);
  }

  @Test // shows how to convert any unit matrix (= no size limits) to DMatrix using our "2D" API
  public void convertUnitMatrix2DAPI() throws XGBoostError, IOException {
    final int ARR_MAX_LEN = 17;

    DMatrix dMatrix = null;
    DMatrix dMatrixSmall = null;
    try {
      Map<String, String> rabitEnv = new HashMap<>();
      rabitEnv.put("DMLC_TASK_ID", "0");
      Rabit.init(rabitEnv);

      final int N = 1000;

      long[][] rowHeaders = createLayout(N + 1, ARR_MAX_LEN).allocateLong(); // headers need +1
      int[][] colIndices = createLayout(N, ARR_MAX_LEN).allocateInt();
      float[][] values = createLayout(N, ARR_MAX_LEN).allocateFloat();

      long pos = 0;
      for (int m = 0; m < N; m++) {
        int arr_idx = (int) (pos / ARR_MAX_LEN);
        int arr_pos = (int) (pos % ARR_MAX_LEN);

        values[arr_idx][arr_pos] = 1;
        colIndices[arr_idx][arr_pos] = m;
        rowHeaders[arr_idx][arr_pos] = pos;
        pos++;
      }
      int arr_idx = (int) (pos / ARR_MAX_LEN);
      int arr_pos = (int) (pos % ARR_MAX_LEN);
      rowHeaders[arr_idx][arr_pos] = pos;
      assertEquals(N, pos);

      // this can be confusing:
      final int shapeParam = N; // number of columns (note: can also be set to 0; 0 means guess from data)
      final int shapeParam2 = N + 1; // number of rows in the matrix + 1
      final long ndata = N; // total number of values

      dMatrix = new DMatrix(rowHeaders, colIndices, values, DMatrix.SparseType.CSR, shapeParam, shapeParam2, ndata);
      assertEquals(N, dMatrix.rowNum());

      // now treat the matrix as small and compare them - they should be bit-to-bit identical
      dMatrixSmall = makeSmallUnitMatrix(N);

      File dmatrixFile = tmp.newFile("dmatrix");
      dMatrix.saveBinary(dmatrixFile.getAbsolutePath());
      File dmatrixSmallFile = tmp.newFile("dmatrixSmall");
      dMatrixSmall.saveBinary(dmatrixSmallFile.getAbsolutePath());

      assertTrue(FileUtils.contentEquals(dmatrixFile, dmatrixSmallFile));
    } finally {
      if (dMatrix != null) {
        dMatrix.dispose();
      }
      if (dMatrixSmall != null) {
        dMatrixSmall.dispose();
      }
      Rabit.shutdown();
    }
  }

  private static Layout createLayout(long size, int maxArrLen) {
    Layout l = new Layout();
    l._numRegRows = (int) (size / maxArrLen);
    l._regRowLen = maxArrLen;
    l._lastRowLen = (int) (size - ((long) l._numRegRows * l._regRowLen)); // allow empty last row (easier and it shouldn't matter)
    return l;
  }

  private static class Layout {
    int _numRegRows;
    int _regRowLen;
    int _lastRowLen;

    long[][] allocateLong() {
      long[][] result = new long[_numRegRows + 1][];
      for (int i = 0; i < _numRegRows; i++) {
        result[i] = new long[_regRowLen];
      }
      result[result.length - 1] = new long[_lastRowLen];
      return result;
    }

    int[][] allocateInt() {
      int[][] result = new int[_numRegRows + 1][];
      for (int i = 0; i < _numRegRows; i++) {
        result[i] = new int[_regRowLen];
      }
      result[result.length - 1] = new int[_lastRowLen];
      return result;
    }

    float[][] allocateFloat() {
      float[][] result = new float[_numRegRows + 1][];
      for (int i = 0; i < _numRegRows; i++) {
        result[i] = new float[_regRowLen];
      }
      result[result.length - 1] = new float[_lastRowLen];
      return result;
    }

  }

}
