package water.rapids.vals;

import water.fvec.Frame;
import water.rapids.Val;

/**
 * Value that represents an H2O dataframe ({@link Frame}).
 */
public class ValFrame extends Val {
  private final Frame _fr;

  public ValFrame(Frame fr) {
    assert fr != null : "Cannot construct a Frame from null";
    _fr = fr;
  }

  @Override public int type() { return FRM; }
  @Override public boolean isFrame() { return true; }
  @Override public Frame getFrame() { return _fr; }
  @Override public String toString() { return _fr.toString(); }

  /**
   * Extract row from a single-row frame.
   * @return Array of row elements.
   */
  @Override public double[] getRow() {
    if (_fr.numRows() != 1)
      throw new IllegalArgumentException("Trying to get a single row from a multirow frame: " + _fr.numRows() + "!=1");

    double res[] = new double[_fr.numCols()];
    for (int i = 0; i < _fr.numCols(); ++i)
      res[i] = _fr.vec(i).at(0);
    return res;
  }
}
