package water.rapids.ast;

import water.rapids.Env;
import water.rapids.vals.ValRow;

/**
 * A Row.  Execution is just to return the constant.
 */
public class AstRow extends AstRoot {
  final ValRow _row;

  public AstRow(double[] ds, String[] names) {
    _row = new ValRow(ds, names);
  }

  @Override
  public String str() {
    return _row.toString();
  }

  @Override
  public String example() {
    return null;
  }

  @Override
  public String description() {
    return null;
  }

  @Override
  public ValRow exec(Env env) {
    return _row;
  }

  @Override
  public int nargs() {
    return 1;
  }
}