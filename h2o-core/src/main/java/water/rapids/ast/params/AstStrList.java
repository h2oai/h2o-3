package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstParameter;
import water.rapids.vals.ValStrs;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A collection of Strings only.  This is a syntatic form only, and never executes and never gets on the execution
 * stack.
 */
public class AstStrList extends AstParameter {
  public String[] _strs;

  public AstStrList() {
    _strs = null;
  }

  public AstStrList(ArrayList<String> strs) {
    _strs = strs.toArray(new String[strs.size()]);
  }

  @Override
  public Val exec(Env env) {
    return new ValStrs(_strs);
  }

  @Override
  public String str() {
    return Arrays.toString(_strs);
  }

  // Select columns by number or String.
  @Override
  public int[] columns(String[] names) {
    int[] idxs = new int[_strs.length];
    for (int i = 0; i < _strs.length; i++) {
      int idx = idxs[i] = water.util.ArrayUtils.find(names, _strs[i]);
      if (idx == -1) throw new IllegalArgumentException("Column " + _strs[i] + " not found");
    }
    return idxs;
  }
}
