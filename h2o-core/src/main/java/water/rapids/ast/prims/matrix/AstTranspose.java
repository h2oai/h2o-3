package water.rapids.ast.prims.matrix;

import hex.DMatrix;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Matrix transposition
 */
public class AstTranspose extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (t X)

  @Override
  public String str() {
    return "t";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    return new ValFrame(DMatrix.transpose(f));
  }
}
