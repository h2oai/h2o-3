package water.rapids.ast.prims.models;

import hex.AUC2;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;

/**
 * Calculates a "perfect" (= not approximated) AUC
 */
public class AstPerfectAUC extends AstPrimitive {

  @Override
  public String[] args() {
    return new String[]{"probs", "acts"};
  }

  @Override
  public int nargs() {
    return 1 + 2;
  } // (perfectAUC probs acts)

  @Override
  public String str() {
    return "perfectAUC";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Vec probs = getSingleVec(stk.track(asts[1].exec(env)), "probabilities");
    Vec acts = getSingleVec(stk.track(asts[2].exec(env)), "actuals");
    double auc = AUC2.perfectAUC(probs, acts);
    return ValFrame.fromRow(auc);
  }

  private static Vec getSingleVec(Val v, String what) {
    Frame f = v.getFrame();
    if (f == null || f.numCols() != 1) {
      throw new IllegalArgumentException("Expected a frame containing a single vector of " + what + 
              ". Instead got " + String.valueOf(f));
    }
    return f.vec(0);
  }

}
