package water.rapids.ast.prims.mungers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValRow;

/**
 */
public class AstGetrow extends AstPrimitive {

  @Override public String[] args() {
    return new String[]{"frame"};
  }

  @Override public int nargs() {
    return 1 + 1;
  }

  @Override public String str() {
    return "getrow";
  }

  @Override public String example() {
    return "(getrow frame)";
  }

  @Override public String description() {
    return "For a single-row frame, this function returns the contents of that frame as a ValRow. " +
           "All non-numeric and non-time columns will be converted into NaNs. " +
           "This function does not work for frames that have more than 1 row.";
  }

  @Override
  public ValRow apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    if (fr.numRows() != 1)
      throw new IllegalArgumentException("The frame should have only 1 row; found " + fr.numRows() + " rows.");

    double[] res = new double[fr.numCols()];
    for (int i = 0; i < res.length; i++) {
      Vec v = fr.vec(i);
      res[i] = v.isNumeric()? v.at(0) : v.isTime()? v.at8(0) : Double.NaN;
    }
    return new ValRow(res, null);
  }
}
