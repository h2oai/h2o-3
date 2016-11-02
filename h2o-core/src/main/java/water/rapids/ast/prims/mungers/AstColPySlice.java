package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstParameter;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValRow;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.params.AstNum;

/**
 * Column slice; allows python-like syntax.
 * Numbers past last column are allowed and ignored in NumLists, but throw an
 * error for single numbers.  Negative numbers have the number of columns
 * added to them, before being checked for range.
 */
public class AstColPySlice extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "cols"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (cols_py src [col_list])

  @Override
  public String str() {
    return "cols_py";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val v = stk.track(asts[1].exec(env));
    AstParameter colList = (AstParameter) asts[2];
    if (v instanceof ValRow) {
      ValRow vv = (ValRow) v;
      return vv.slice(colList.columns(vv.getNames()));
    }
    Frame fr = v.getFrame();
    int[] cols = colList.columns(fr.names());

    Frame fr2 = new Frame();
    if (cols.length == 0)        // Empty inclusion list?
      return new ValFrame(fr2);
    if (cols[0] < 0)           // Negative cols have number of cols added
      for (int i = 0; i < cols.length; i++)
        cols[i] += fr.numCols();
    if (asts[2] instanceof AstNum && // Singletons must be in-range
        (cols[0] < 0 || cols[0] >= fr.numCols()))
      throw new IllegalArgumentException("Column must be an integer from 0 to " + (fr.numCols() - 1));
    for (int col : cols)       // For all included columns
      if (col >= 0 && col < fr.numCols()) // Ignoring out-of-range ones
        fr2.add(fr.names()[col], fr.vecs()[col]);
    return new ValFrame(fr2);
  }
}
