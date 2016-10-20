package water.rapids.ast.prims.reducers;

import water.Key;
import water.MRTask;
import water.fvec.*;
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
      return axis? rowwiseMean(fr, na_rm) : colwiseMean(fr, na_rm);
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


  /**
   * Compute Frame means by rows, and return a frame consisting of a single Vec of means in each row.
   */
  private ValFrame rowwiseMean(Frame fr, final boolean na_rm) {
    String[] newnames = {"mean"};
    Key<Frame> newkey = Key.make();

    // Determine how many columns of different types we have
    int n_numeric = 0, n_time = 0;
    for (Vec vec : fr.vecs()) {
      if (vec.isNumeric()) n_numeric++;
      if (vec.isTime()) n_time++;
    }
    // Compute the type of the resulting column: if all columns are TIME then the result is also time; otherwise
    // if at least one column is numeric then the result is also numeric.
    byte resType = n_numeric > 0? Vec.T_NUM : Vec.T_TIME;

    // Construct the frame over which the mean should be computed
    Frame compFrame = new Frame();
    for (int i = 0; i < fr.numCols(); i++) {
      Vec vec = fr.vec(i);
      if (n_numeric > 0? vec.isNumeric() : vec.isTime())
        compFrame.add(fr.name(i), vec);
    }
    Vec anyvec = compFrame.anyVec();

    // Take into account certain corner cases
    if (anyvec == null) {
      Frame res = new Frame(newkey);
      anyvec = fr.anyVec();
      if (anyvec != null) {
        // All columns in the original frame are non-numeric -> return a vec of NAs
        res.add("mean", anyvec.makeCon(Double.NaN));
      } // else the original frame is empty, in which case we return an empty frame too
      return new ValFrame(res);
    }
    if (!na_rm && n_numeric < fr.numCols() && n_time < fr.numCols()) {
      // If some of the columns are non-numeric and na_rm==false, then the result is a vec of NAs
      Frame res = new Frame(newkey, newnames, new Vec[]{anyvec.makeCon(Double.NaN)});
      return new ValFrame(res);
    }

    // Compute the mean over all rows
    final int numCols = compFrame.numCols();
    Frame res = new MRTask() {
      @Override
      public void map(Chunk[] cs, NewChunk nc) {
        for (int i = 0; i < cs[0]._len; i++) {
          double d = 0;
          int numNaColumns = 0;
          for (int j = 0; j < numCols; j++) {
            double val = cs[j].atd(i);
            if (Double.isNaN(val))
              numNaColumns++;
            else
              d += val;
          }
          if (na_rm? numNaColumns < numCols : numNaColumns == 0)
            nc.addNum(d / (numCols - numNaColumns));
          else
            nc.addNum(Double.NaN);
        }
      }
    }.doAll(1, resType, compFrame)
     .outputFrame(newkey, newnames, null);

    // Return the result
    return new ValFrame(res);
  }


  /**
   * Compute column-wise means (i.e. means of each column), and return a frame having a single row.
   */
  private ValFrame colwiseMean(Frame fr, final boolean na_rm) {
    Frame res = new Frame();

    Vec vec1 = Vec.makeCon(null, 0);
    assert vec1.length() == 1;

    for (int i = 0; i < fr.numCols(); i++) {
      Vec v = fr.vec(i);
      boolean valid = (v.isNumeric() || v.isTime() || v.isBinary()) && v.length() > 0 && (na_rm || v.naCnt() == 0);
      Vec newvec = vec1.makeCon(valid? v.mean() : Double.NaN, v.isTime()? Vec.T_TIME : Vec.T_NUM);
      res.add(fr.name(i), newvec);
    }

    vec1.remove();
    return new ValFrame(res);
  }
}
