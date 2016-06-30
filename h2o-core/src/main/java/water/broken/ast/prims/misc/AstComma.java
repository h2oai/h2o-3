package water.rapids.ast.prims.misc;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Evaluate any number of expressions, returning the last one
 */
public class AstComma extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"..."};
  }

  @Override
  public int nargs() {
    return -1;
  } // variable args

  @Override
  public String str() {
    return ",";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val val = new ValNum(0);
    for (int i = 1; i < asts.length; i++)
      val = stk.track(asts[i].exec(env));  // Evaluate all expressions for side-effects
    return val;  // Return the last one
  }
}
