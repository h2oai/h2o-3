package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.VecAry;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.util.VecUtils;

/**
 * Convert to a factor/categorical
 */
public class AstAsFactor extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  @Override
  public int nargs() {
    return 1 + 1;
  } // (as.factor col)

  @Override
  public String str() {
    return "as.factor";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame ary = stk.track(asts[1].exec(env)).getFrame();
    VecAry vary = ary.vecs();
    // Type check  - prescreen for correct types
    for (int i = 0; i < vary.numCols(); ++i)
      if (!(vary.isCategorical(i) || vary.isString(i) || vary.isNumeric(i)))
        throw new IllegalArgumentException("asfactor() requires a string, categorical, or numeric column. "
            + "Received " + ary.anyVec().get_type_str()
            + ". Please convert column to a string or categorical first.");
    return new ValFrame(new Frame(ary._names, new VecAry(vary.toCategoricalVec())));
  }
}
