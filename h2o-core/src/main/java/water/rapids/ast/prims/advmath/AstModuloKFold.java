package water.rapids.ast.prims.advmath;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstModuloKFold extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "nfolds"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (modulo_kfold_column x nfolds)

  @Override
  public String str() {
    return "modulo_kfold_column";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Vec foldVec = stk.track(asts[1].exec(env)).getFrame().anyVec().makeZero();
    int nfolds = (int) asts[2].exec(env).getNum();
    return new ValFrame(new Frame(AstKFold.moduloKfoldColumn(foldVec, nfolds)));
  }
}
