package water.rapids.vals;

import water.Keyed;
import water.rapids.Val;

public class ValKeyed extends Val {
  private final Keyed _k;

  public ValKeyed(Keyed k) {
    assert k != null : "Cannot construct a Keyed from null";
    _k = k;
  }

  @Override public int type() { return KEYED; }
  @Override public boolean isKeyed() { return true; }
  @Override public Keyed getKeyed() { return _k; }
  @Override public String toString() { return _k.toString(); }

}
