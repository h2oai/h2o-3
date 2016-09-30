package water.rapids.ast.params;

import water.rapids.Env;
import water.rapids.Rapids;
import water.rapids.Val;
import water.rapids.ast.AstParameter;

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

  public AstStrList(Rapids e) {
    ArrayList<String> strs = new ArrayList<>();
    while (true) {
      char c = e.skipWS();
      if (c == ']') break;
      if (Rapids.isQuote(c)) strs.add(e.match(c));
      else throw new IllegalArgumentException("Expecting the start of a string");
    }
    e.xpeek(']');
    _strs = strs.toArray(new String[strs.size()]);
  }

  // Strange count of args, due to custom parsing
  @Override
  public int nargs() {
    return -1;
  }

  // This is a special syntatic form; the number-list never executes and hits the execution stack
  @Override
  public Val exec(Env env) {
    throw new IllegalArgumentException("String list not allowed here");
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
