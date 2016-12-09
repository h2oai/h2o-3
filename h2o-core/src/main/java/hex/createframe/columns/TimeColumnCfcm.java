package hex.createframe.columns;

import hex.createframe.CreateFrameColumnMaker;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Random;

/**
 * Time-valued random column.
 */
public class TimeColumnCfcm extends CreateFrameColumnMaker {
  private String name;
  private long lowerBound;
  private long upperBound;

  public TimeColumnCfcm() {}
  public TimeColumnCfcm(String colName, long lBound, long uBound) {
    name = colName;
    lowerBound = lBound;
    upperBound = uBound;
  }

  @Override public void exec(int nrows, NewChunk[] ncs, Random rng) {
    long span = upperBound - lowerBound + 1;
    for (int row = 0; row < nrows; ++row)
      ncs[index].addNum(lowerBound + (long)(rng.nextDouble() * span));
  }

  @Override public String[] columnNames() {
    return new String[]{name};
  }

  @Override public byte[] columnTypes() {
    return new byte[]{Vec.T_TIME};
  }

  @Override public float byteSizePerRow() {
    return 8;
  }
}
