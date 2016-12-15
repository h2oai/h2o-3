package water.rapids.ast.prims.advmath;

import water.fvec.*;
import water.operations.PearsonCorrelation;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValNum;

/**
 * Calculate Pearson's Correlation Coefficient between columns of a frame
 * <p/>
 * Formula:
 * Pearson's Correlation Coefficient = Cov(X,Y)/sigma(X) * sigma(Y)
 */
public class AstCorrelation extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "x", "y", "use"};
  }


  @Override
  public int nargs() {
    return 1 + 3; /* (cor X Y use) */
  }

  @Override
  public String str() {
    return "cor";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame frx = stk.track(asts[1].exec(env)).getFrame();
    Frame fry = stk.track(asts[2].exec(env)).getFrame();
      if (frx.numRows() != fry.numRows()) {
      throw new IllegalArgumentException("Frames must have the same number of rows, found " + frx.numRows() + " and " + fry.numRows());
    }

      String stringMode = stk.track(asts[3].exec(env)).getStr();
      PearsonCorrelation.Mode mode = PearsonCorrelation.convertMode(stringMode);

      return fry.numRows() == 1 ? new ValNum(PearsonCorrelation.pearsonCorrelation(frx, fry, mode)) : PearsonCorrelation.array(frx, fry, mode);
  }


}