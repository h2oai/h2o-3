package water.rapids.ast.prims.assign;

import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Assign a temp.  All such assignments are final (cannot change), but the temp can be deleted.  Temp is returned for
 * immediate use, and also set in the DKV.  Must be globally unique in the DKV.
 */
public class AstTmpAssign extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"id", "frame"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (tmp= id frame)

  @Override
  public String str() {
    return "tmp=";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Key<Frame> id = Key.make(asts[1].str());
    if (DKV.get(id) != null) throw new IllegalArgumentException("Temp ID " + id + " already exists");
    Frame src = stk.track(asts[2].exec(env)).getFrame();
    Frame dst = new Frame(id, src._names, src.vecs());
    return new ValFrame(env._ses.track_tmp(dst)); // Track new session-wide ID
  }
}
