package water.rapids.ast.prims.reducers;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.vals.ValNum;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

/**
 */
public class AstMedian extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "method"};
  }

  @Override
  public String str() {
    return "median";
  }

  @Override
  public int nargs() {
    return 1 + 2;
  }  // (median fr method)

  @Override
  public ValNum apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    boolean narm = asts[2].exec(env).getNum() == 1;
    if (!narm && (fr.anyVec().length() == 0 || fr.anyVec().naCnt() > 0)) return new ValNum(Double.NaN);
    // does linear interpolation for even sample sizes by default
    return new ValNum(median(fr, QuantileModel.CombineMethod.INTERPOLATE));
  }

  public static double median(Frame fr, QuantileModel.CombineMethod combine_method) {
    if (fr.numCols() != 1 || !fr.anyVec().isNumeric())
      throw new IllegalArgumentException("median only works on a single numeric column");
    // Frame needs a Key for Quantile, might not have one from rapids
    Key tk = null;
    if (fr._key == null) {
      DKV.put(tk = Key.make(), fr = new Frame(tk, fr.names(), fr.vecs()));
    }
    // Quantiles to get the median
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._probs = new double[]{0.5};
    parms._train = fr._key;
    parms._combine_method = combine_method;
    QuantileModel q = new Quantile(parms).trainModel().get();
    double median = q._output._quantiles[0][0];
    q.delete();
    if (tk != null) {
      DKV.remove(tk);
    }
    return median;
  }

  static double median(Vec v, QuantileModel.CombineMethod combine_method) {
    return median(new Frame(v), combine_method);
  }
}
