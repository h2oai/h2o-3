package water.rapids.ast.prims.assign;

import water.DKV;
import water.Key;
import water.Keyed;
import water.Value;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 * Remove by ID.  Removing a Frame updates refcnts.  Returns 1 for removing, 0 if id does not exist.
 */
public class AstRm extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"id"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (rm id)

  @Override
  public String str() {
    return "rm";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Key id = Key.make(env.expand(asts[1].str()));
    Value val = DKV.get(id);
    if (val == null) return new ValNum(0);
    if (val.isFrame())
      env._ses.remove(val.<Frame>get()); // Remove unshared Vecs
    else
      Keyed.remove(id);           // Normal (e.g. Model) remove

    return new ValNum(1);
  }
}
