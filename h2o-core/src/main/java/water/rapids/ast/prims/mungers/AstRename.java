package water.rapids.ast.prims.mungers;

import hex.Model;
import water.DKV;
import water.Iced;
import water.Key;
import water.fvec.Frame;
import water.rapids.Env;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;

/**
 */
public class AstRename extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"oldId", "newId"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (rename oldId newId)

  @Override
  public String str() {
    return "rename";
  }

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Key oldKey = Key.make(env.expand(asts[1].exec(env).getStr()));
    Key newKey = Key.make(env.expand(asts[2].exec(env).getStr()));
    Iced o = DKV.remove(oldKey).get();
    if (o instanceof Frame)
      DKV.put(newKey, new Frame(newKey, ((Frame) o)._names, ((Frame) o).vecs()));
    else if (o instanceof Model) {
      ((Model) o)._key = newKey;
      DKV.put(newKey, o);
    } else
      throw new IllegalArgumentException("Trying to rename Value of type " + o.getClass());

    return new ValNum(Double.NaN);
  }
}
