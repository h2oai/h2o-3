package water.rapids.ast.prims.matrix;

import hex.DMatrix;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Matrix multiplication
 */
public class AstMMult extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "ary2"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (x X1 X2)

  @Override
  public String str() {
    return "x";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame X1 = stk.track(asts[1].exec(env)).getFrame();
    Frame X2 = stk.track(asts[2].exec(env)).getFrame();
    return new ValFrame(DMatrix.mmul(X1, X2));
  }
}
