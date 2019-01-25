package hex.coxph;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;

public class StorageTest extends TestUtil {

  @BeforeClass()
  public static void setup() { stall_till_cloudsize(1); }

  @Test
  public void testToFrame() {
    try {
      Scope.enter();
      Storage.AbstractMatrix<?> matrix = new TestReadOnlyMatrix(7, 3);
      Key<Frame> key = Key.make();
      Frame f = Scope.track(matrix.toFrame(key));
      Assert.assertEquals(key, f._key);
      Frame expected = Scope.track(new TestFrameBuilder()
              .withName("testFrame")
              .withColNames("ColA", "ColB", "ColC")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(1, 2, 3, 4, 5, 6, 7))
              .withDataForCol(1, ard(2, 4, 6, 8, 10, 12, 14))
              .withDataForCol(2, ard(3, 6, 9, 12, 15, 18, 21))
              .withChunkLayout(2, 2, 2, 1)
              .build());
      for (int i = 0; i < f.numCols(); i++)
        assertVecEquals(expected.vec(i), f.vec(i), 0);
    } finally {
      Scope.exit();
    }
  }

  static class TestReadOnlyMatrix extends Storage.AbstractMatrix<TestReadOnlyMatrix> {
    int _row;
    int _col;

    private TestReadOnlyMatrix(int row, int col) {
      _row = row;
      _col = col;
    }

    @Override
    public double get(int row, int col) {
      return (row + 1) * (col + 1);
    }

    @Override
    public void set(int row, int col, double val) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void add(int row, int col, double val) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int cols() {
      return _col;
    }

    @Override
    public int rows() {
      return _row;
    }

    @Override
    public long size() {
      return _row * _col;
    }

    @Override
    public double[] raw() {
      throw new UnsupportedOperationException();
    }
  }

}