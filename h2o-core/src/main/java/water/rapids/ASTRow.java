package water.rapids;

/** A Row.  Execution is just to return the constant. */
public class ASTRow extends AST {
  final ValRow _row;
  public ASTRow(double[] ds, String[] names) { _row = new ValRow(ds,names); }
  @Override public String str() { return _row.toString(); }
  @Override public String example() { return null; }
  @Override public String description() { return null; }
  @Override public ValRow exec(Env env) { return _row; }
  @Override int nargs() { return 1; }
}