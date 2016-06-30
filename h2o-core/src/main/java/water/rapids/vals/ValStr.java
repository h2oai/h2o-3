package water.rapids.vals;

import water.rapids.Val;

/**
 * A string
 */
public class ValStr extends Val {
  private final String _str;

  public ValStr(String str) {
    _str = str;
  }

  @Override public int type() { return STR; }
  @Override public boolean isStr() { return true; }
  @Override public String getStr() { return _str; }

  // TODO: is this even safe? what if _str contains quotation marks, backslashes, etc?
  @Override public String toString() { return '"' + _str + '"'; }
}
