package water.udf;

import water.fvec.Vec;

/**
 * Vec coming from a column
 */
public class VirtualVec extends Vec {
  public <T> VirtualVec(Column<T> column) {
    super(null, column.rowLayout());
  }
}
