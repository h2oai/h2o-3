package water.rapids.ast.prims.assign;

import water.DKV;
import water.Key;
import water.Value;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

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
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    // Note: non-standard evaluation of the first argument! Instead of being
    // executed, it is stringified. This, for example, allows us to write an
    // expression as
    //     (tmp= newid (* frame 3))
    // instead of
    //     (tmp= "newid" (* frame 3))
    // On the other hand, this makes us unable to create dynamic identifiers
    // in Rapids, for example this is invalid:
    //     (tmp= (+ "id" 3) (* frame 3))
    // Right now there is no need for dynamically generated identifiers, since
    // we don't even have proper variables or loops or control structures yet.
    //
    Key<Frame> id = Key.make(env.expand(asts[1].str()));
    Val srcVal = stk.track(asts[2].exec(env));
    Frame srcFrame = srcVal.getFrame();

    Value v = DKV.get(id);
    if (v != null) {
      if (v.get().equals(srcFrame))
        return (ValFrame) srcVal;
      else
        throw new IllegalArgumentException("Temp ID " + id + " already exists");
    }
    Frame dst = new Frame(id, srcFrame._names, srcFrame.vecs());
    return new ValFrame(env._ses.track_tmp(dst)); // Track new session-wide ID
  }
}
