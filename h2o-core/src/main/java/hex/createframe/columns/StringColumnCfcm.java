package hex.createframe.columns;

import hex.createframe.CreateFrameColumnMaker;
import water.fvec.NewChunk;
import water.fvec.Vec;

import java.util.Random;

/**
 * Random string column.
 */
public class StringColumnCfcm extends CreateFrameColumnMaker {
  private String name;
  private int len;

  public StringColumnCfcm() {}

  public StringColumnCfcm(String colName, int length) {
    name = colName;
    len = length;
  }

  @Override public void exec(int nrows, NewChunk[] ncs, Random rng) {
    byte[] buf = new byte[len];
    for (int row = 0; row < nrows; ++row) {
      for (int i = 0; i < len; ++i)
        buf[i] = (byte)(65 + rng.nextInt(25));
      ncs[index].addStr(new String(buf));
    }
  }

  @Override public String[] columnNames() {
    return new String[]{name};
  }

  @Override public byte[] columnTypes() {
    return new byte[]{Vec.T_STR};
  }

  @Override public float byteSizePerRow() {
    return len;
  }

  @Override public int workAmount() {
    return 60 + len * 50;
  }

}
