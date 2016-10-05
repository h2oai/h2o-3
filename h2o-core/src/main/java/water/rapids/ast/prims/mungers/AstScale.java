package water.rapids.ast.prims.mungers;

import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNumList;

import java.util.Arrays;

/**
 * Center and scale a frame.  Can be passed in the centers and scales (one per column in an number list), or a
 * TRUE/FALSE.
*/
public class AstScale extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "center", "scale"};
  }

  @Override
  public int nargs() {
    return 1 + 3;
  } // (scale x center scale)

  @Override
  public String str() {
    return "scale";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    int ncols = fr.numCols();

    // Peel out the bias/shift/mean
    double[] means;
    if (asts[2] instanceof AstNumList) {
      means = ((AstNumList) asts[2]).expand();
      if (means.length != ncols)
        throw new IllegalArgumentException("Numlist must be the same length as the columns of the Frame");
    } else {
      double d = asts[2].exec(env).getNum();
      if (d == 0) means = new double[ncols]; // No change on means, so zero-filled
      else if (d == 1) means = fr.means();
      else throw new IllegalArgumentException("Only true or false allowed");
    }

    // Peel out the scale/stddev
    double[] mults;
    if (asts[3] instanceof AstNumList) {
      mults = ((AstNumList) asts[3]).expand();
      if (mults.length != ncols)
        throw new IllegalArgumentException("Numlist must be the same length as the columns of the Frame");
    } else {
      Val v = asts[3].exec(env);
      if (v instanceof ValFrame) {
        mults = toArray(v.getFrame().anyVec());
      } else {
        double d = v.getNum();
        if (d == 0)
          Arrays.fill(mults = new double[ncols], 1.0); // No change on mults, so one-filled
        else if (d == 1) mults = fr.mults();
        else throw new IllegalArgumentException("Only true or false allowed");
      }
    }

    // Update in-place.
    final double[] fmeans = means; // Make final copy for closure
    final double[] fmults = mults; // Make final copy for closure
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i = 0; i < cs.length; i++)
          for (int row = 0; row < cs[i]._len; row++)
            cs[i].set(row, (cs[i].atd(row) - fmeans[i]) * fmults[i]);
      }
    }.doAll(fr);
    return new ValFrame(fr);
  }

  private static double[] toArray(Vec v) {
    double[] res = new double[(int) v.length()];
    for (int i = 0; i < res.length; ++i)
      res[i] = v.at(i);
    return res;
  }
}
