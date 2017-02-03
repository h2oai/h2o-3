package water.rapids.vals;

import water.rapids.Val;

import java.util.Arrays;

/**
 * Row (array) of numbers.
 */
public class ValRow extends Val {
  private final double[] _ds;
  private final String[] _names;

  public ValRow(double[] ds, String[] names) {
    _ds = ds;
    _names = names;
    if (ds != null && names != null && ds.length != names.length)
      throw new IllegalArgumentException("Lengths of data and names mismatch: " +
          Arrays.toString(ds) + " and " + Arrays.toString(names));
  }

  @Override public int type() { return ROW; }
  @Override public boolean isRow() { return true; }
  @Override public double[] getRow() { return _ds; }
  @Override public double[] getNums() { return _ds; }
  @Override public String toString() { return Arrays.toString(_ds); }

  public String[] getNames() {
    return _names;
  }

  /**
   * Creates a new ValRow by selecting elements at the specified indices.
   * @param cols array of indices to select. We do not check for AIOOB errors.
   * @return new ValRow object
   */
  public ValRow slice(int[] cols) {
    double[] ds = new double[cols.length];
    String[] ns = new String[cols.length];
    for (int i = 0; i < cols.length; ++i) {
      ds[i] = _ds[cols[i]];
      ns[i] = _names[cols[i]];
    }
    return new ValRow(ds, ns);
  }
}
