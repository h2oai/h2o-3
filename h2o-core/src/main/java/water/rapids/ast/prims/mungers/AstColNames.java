package water.rapids.ast.prims.mungers;

import water.DKV;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.params.AstNum;
import water.rapids.ast.params.AstNumList;
import water.rapids.ast.params.AstStrList;

/**
 * Assign column names
 */
public class AstColNames extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "cols", "names"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (colnames frame [#cols] ["names"])

  @Override
  public String str() {
    return "colnames=";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (asts[2] instanceof AstNumList) {
      if (!(asts[3] instanceof AstStrList))
        throw new IllegalArgumentException("Column naming requires a string-list, but found a " + asts[3].getClass());
      AstNumList cols = ((AstNumList) asts[2]);
      AstStrList nams = ((AstStrList) asts[3]);
      int d[] = cols.expand4();
      if (d.length != nams._strs.length)
        throw new IllegalArgumentException("Must have the same number of column choices as names");
      for (int i = 0; i < d.length; i++)
        fr._names[d[i]] = nams._strs[i];

    } else if ((asts[2] instanceof AstNum)) {
      int col = (int) (asts[2].exec(env).getNum());
      String name = asts[3].exec(env).getStr();
      fr._names[col] = name;
    } else
      throw new IllegalArgumentException("Column naming requires a number-list, but found a " + asts[2].getClass());
    if (fr._key != null) DKV.put(fr); // Update names in DKV
    return new ValFrame(fr);
  }
}

