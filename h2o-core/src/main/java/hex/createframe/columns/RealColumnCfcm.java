package hex.createframe.columns;

import hex.createframe.CreateFrameColumnMaker;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Random;

/**
 * Real-valued random column.
 */
public class RealColumnCfcm extends CreateFrameColumnMaker {
  private String name;
  private double lowerBound;
  private double upperBound;

  public RealColumnCfcm() {}

  public RealColumnCfcm(String colName, double lBound, double uBound) {
    name = colName;
    lowerBound = lBound;
    upperBound = uBound;
  }

  @Override public void exec(int nrows, NewChunk[] ncs, Random rng) {
    double span = upperBound - lowerBound;
    for (int row = 0; row < nrows; ++row)
      ncs[index].addNum(lowerBound + (span == 0? 0 : rng.nextDouble() * span));
  }

  @Override public String[] columnNames() {
    return new String[]{name};
  }

  @Override public float byteSizePerRow() {
    return 8;
  }

  @Override public byte[] columnTypes() {
    return new byte[]{Vec.T_NUM};
  }

}
