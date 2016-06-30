package water.rapids.vals;

import water.rapids.Val;

import java.util.Arrays;

/**
 * Array of strings.
 */
public class ValStrs extends Val {
  private final String[] _strs;

  ValStrs(String[] strs) {
    _strs = strs;
  }

  @Override public int type() { return STRS; }
  @Override public boolean isStrs() { return true; }
  @Override public String[] getStrs() { return _strs; }

  @Override public String toString() { return Arrays.toString(_strs); }
}
