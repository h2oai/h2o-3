package water.rapids.ast.prims.matrix;

import hex.DMatrix;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.Ast;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstFunction;

/**
 * Matrix transposition
 */
public class AstTranspose extends AstFunction {
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
  public ValFrame apply(Env env, Env.StackHelp stk, Ast asts[]) {
    Frame f = stk.track(asts[1].exec(env)).getFrame();
    return new ValFrame(DMatrix.transpose(f));
  }
}
