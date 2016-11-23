package hex.createframe.columns;

import hex.createframe.CreateFrameColumnMaker;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Random;

/**
 * Integer-valued random column.
 */
public class IntegerColumnCfcm extends CreateFrameColumnMaker {
  private String name;
  private long lowerBound;
  private long upperBound;

  public IntegerColumnCfcm() {}

  public IntegerColumnCfcm(String colName, int lBound, int uBound) {
    name = colName;
    lowerBound = lBound;
    upperBound = uBound;
  }

  @Override public void exec(int nrows, NewChunk[] ncs, Random rng) {
    long span = upperBound - lowerBound + 1;
    if (span == 1) {
      for (int row = 0; row < nrows; ++row)
        ncs[index].addNum(lowerBound);
    } else {
      for (int row = 0; row < nrows; ++row)
        ncs[index].addNum(lowerBound + (long)(rng.nextDouble()*span));
    }
  }

  @Override public String[] columnNames() {
    return new String[]{name};
  }

  @Override public byte[] columnTypes() {
    return new byte[]{Vec.T_NUM};
  }

  @Override public float byteSizePerRow() {
    long integer_range = Math.max(Math.abs(upperBound), Math.abs(lowerBound));
    if (integer_range < 128) return 1;
    if (integer_range < 32768) return 2;
    if (integer_range < 1L << 31) return 4;
    return 8;
  }
}
