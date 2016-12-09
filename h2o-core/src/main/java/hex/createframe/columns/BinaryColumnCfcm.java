package hex.createframe.columns;

import hex.createframe.CreateFrameColumnMaker;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Random;

/**
 * Random binary column.
 */
public class BinaryColumnCfcm extends CreateFrameColumnMaker {
  private String name;
  private double p;

  public BinaryColumnCfcm() {
  }

  public BinaryColumnCfcm(String colName, double ones_fraction) {
    name = colName;
    p = ones_fraction;
  }

  @Override public void exec(int nrows, NewChunk[] ncs, Random rng) {
    for (int row = 0; row < nrows; ++row)
      ncs[index].addNum(rng.nextFloat() <= p? 1 : 0);
  }

  @Override public String[] columnNames() {
    return new String[]{name};
  }

  @Override public byte[] columnTypes() {
    return new byte[]{Vec.T_NUM};
  }

  @Override public float byteSizePerRow() {
    return 0.125f;
  }
}
