package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValRow;
import water.rapids.ast.AstPrimitive;

import java.util.Arrays;

/**
 * Column slice; allows R-like syntax.
 * Numbers past the largest column are an error.
 * Negative numbers and number lists are allowed, and represent an *exclusion* list
 */
public class AstColSlice extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "cols"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (cols src [col_list])

  @Override
  public String str() {
    return "cols";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val v = stk.track(asts[1].exec(env));
    AstParameter col_list = (AstParameter) asts[2];
    if (v instanceof ValRow) {
      ValRow vv = (ValRow) v;
      return vv.slice(col_list.columns(vv.getNames()));
    }
    Frame src = v.getFrame();
    int[] cols = col_select(src.names(), col_list);
    Frame dst = new Frame();
    Vec[] vecs = src.vecs();
    for (int col : cols) dst.add(src._names[col], vecs[col]);
    return new ValFrame(dst);
  }

  // Complex column selector; by list of names or list of numbers or single
  // name or number.  Numbers can be ranges or negative.
  public static int[] col_select(String[] names, AstParameter col_selector) {
    int[] cols = col_selector.columns(names);
    if (cols.length == 0) return cols; // Empty inclusion list?
    if (cols[0] >= 0) { // Positive (inclusion) list
      if (cols[cols.length - 1] >= names.length)
        throw new IllegalArgumentException("Column must be an integer from 0 to " + (names.length - 1));
      return cols;
    }

    // Negative (exclusion) list; convert to positive inclusion list
    int[] pos = new int[names.length];
    for (int col : cols) // more or less a radix sort, filtering down to cols to ignore
      if (0 <= -col - 1 && -col - 1 < names.length)
        pos[-col - 1] = -1;
    int j = 0;
    for (int i = 0; i < names.length; i++) if (pos[i] == 0) pos[j++] = i;
    return Arrays.copyOfRange(pos, 0, j);
  }

}

