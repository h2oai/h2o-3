package water.rapids.ast.prims.mungers;

import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;

/**
 * cbind: bind columns together into a new frame
 */
public class AstCBind extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"..."};
  }

  @Override
  public int nargs() {
    return -1;
  } // variable number of args

  @Override
  public String str() {
    return "cbind";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {

    // Compute the variable args.  Find the common row count
    Val vals[] = new Val[asts.length];
    Vec vec = null;
    for (int i = 1; i < asts.length; i++) {
      vals[i] = stk.track(asts[i].exec(env));
      if (vals[i].isFrame()) {
        Vec anyvec = vals[i].getFrame().anyVec();
        if (anyvec == null) continue; // Ignore the empty frame
        if (vec == null) vec = anyvec;
        else if (vec.length() != anyvec.length())
          throw new IllegalArgumentException("cbind frames must have all the same rows, found " + vec.length() + " and " + anyvec.length() + " rows.");
      }
    }
    boolean clean = false;
    if (vec == null) {
      vec = Vec.makeZero(1);
      clean = true;
    } // Default to length 1

    // Populate the new Frame
    Frame fr = new Frame();
    for (int i = 1; i < asts.length; i++) {
      switch (vals[i].type()) {
        case Val.FRM:
          fr.add(vals[i].getFrame().names(), fr.makeCompatible(vals[i].getFrame()));
          break;
        case Val.FUN:
          throw H2O.unimpl();
        case Val.STR:
          throw H2O.unimpl();
        case Val.NUM:
          // Auto-expand scalars to fill every row
          double d = vals[i].getNum();
          fr.add(Double.toString(d), vec.makeCon(d));
          break;
        default:
          throw H2O.unimpl();
      }
    }
    if (clean) vec.remove();

    return new ValFrame(fr);
  }
}
