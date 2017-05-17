package water.rapids.vals;

import hex.Model;
import water.rapids.Val;

public class ValModel extends Val {
  private final Model _m;

  public ValModel(Model m) {
    assert m != null : "Cannot construct a Model from null";
    _m = m;
  }

  @Override public int type() { return MOD; }
  @Override public boolean isModel() { return true; }
  @Override public Model getModel() { return _m; }
  @Override public String toString() { return _m.toString(); }

}
