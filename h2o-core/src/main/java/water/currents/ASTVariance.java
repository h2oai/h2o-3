package water.currents;

import water.H2O;
import water.fvec.Frame;

/** Variance between columns of a frame */
class ASTVariance extends ASTPrim {
  @Override int nargs() { return 1+1; }
  @Override public String str() { return "var"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    throw H2O.unimpl();
  }
}
