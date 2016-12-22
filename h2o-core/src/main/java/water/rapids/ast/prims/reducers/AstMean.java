package water.rapids.ast.prims.reducers;

import water.fvec.*;
import water.operations.ColumnWise;
import water.operations.RowWise;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.vals.ValRow;


public class AstMean extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"frame", "na_rm", "axis"};
  }

  @Override
  public String str() {
    return "mean";
  }

  @Override
  public int nargs() {
    return -1;  // 1 + 3;
  }

  @Override
  public String example() {
    return "(mean frame na_rm axis)";
  }

  @Override
  public String description() {
    return "Compute the mean values within the provided frame. If axis = 0, then the mean is computed " +
           "column-wise, and the result is a frame of shape [1 x ncols], where ncols is the number of columns in " +
           "the original frame. If axis = 1, then the mean is computed row-wise, and the result is a frame of shape " +
           "[nrows x 1], where nrows is the number of rows in the original frame. Flag na_rm controls treatment of " +
           "the NA values: if it is 1, then NAs are ignored; if it is 0, then presence of NAs renders the result " +
           "in that column (row) also NA.\n" +
           "Mean of a double / integer / binary column is a double value. Mean of a categorical / string / uuid " +
           "column is NA. Mean of a time column is time. Mean of a column with 0 rows is NaN.\n" +
           "When computing row-wise means, we try not to mix columns of different types. In particular, if there " +
           "are any numeric columns, then all time columns are omitted from computation. However when computing " +
           "mean over multiple time columns, then the Time result is returned. Lastly, binary columns are treated " +
           "as NAs always.";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot[] asts) {
    Val val1 = asts[1].exec(env);
    if (val1 instanceof ValFrame) {
      Frame fr = stk.track(val1).getFrame();
      boolean na_rm = asts[2].exec(env).getNum() == 1;
      boolean axis = asts.length == 4 && (asts[3].exec(env).getNum() == 1);
        return axis ? RowWise.mean(fr, na_rm) : ColumnWise.mean(fr, na_rm);
    }
    else if (val1 instanceof ValRow) {
      // This may be called from AstApply when doing per-row computations.
      double[] row = val1.getRow();
      boolean na_rm = asts[2].exec(env).getNum() == 1;
      double d = 0;
      int n = 0;
      for (double r: row) {
        if (Double.isNaN(r)) {
          if (!na_rm)
            return new ValRow(new double[]{Double.NaN}, null);
        } else {
          d += r;
          n++;
        }
      }
      return new ValRow(new double[]{d / n}, null);
    } else
      throw new IllegalArgumentException("Incorrect argument to (mean): expected a frame or a row, received " + val1.getClass());
  }


}
