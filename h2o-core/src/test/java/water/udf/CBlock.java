package water.udf;

import org.junit.Ignore;

import water.fvec.Chunk;

@Ignore("Support for tests, but no actual tests here")
public class CBlock {

  protected CBlock(Chunk[] c) {
    this(c, 0, c.length);
  }

  protected CBlock(Chunk[] c, int off, int len) {
    assert c != null : "Chunk array cannot be null!";
    this.c = c;
    this.off = off;
    this.len = len;
  }

  public class CRow {
    private int row;

    public double readDouble(int col) {
      return column(col).atd(row);
    }

    public long readLong(int col) {
      return column(col).at8(row);
    }

    public double[] readDoubles() {
      double[] res = new double[len()];
      for (int i = 0; i < len; i++) {
        res[i] = readDouble(i);
      }
      return res;
    }

    private CRow setRow(int row) {
      this.row = row;
      return this;
    }

    public int len() {
      return len;
    }
  }

  final private int off;
  final private int len;
  final private Chunk[] c;
  final private CRow row = new CRow();

  public int columns() {
    return len;
  }

  public int rows() {
    return c[0]._len;
  }

  private final Chunk column(int col) {
    return c[off + col];
  }

  public CRow row(int idx) {
    return row.setRow(idx);
  }
}
