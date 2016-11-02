package water.rapids.ast.prims.assign;

import water.*;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Assign a whole frame over a global.  Copy-On-Write optimizations make this cheap.
 */
public class AstAssign extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"id", "frame"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (assign id frame)

  @Override
  public String str() {
    return "assign";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Key<Frame> id = Key.make(asts[1].str());
    Frame src = stk.track(asts[2].exec(env)).getFrame();
    return new ValFrame(env._ses.assign(id, src)); // New global Frame over shared Vecs
  }
}

