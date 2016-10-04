package water.rapids.ast.prims.advmath;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.Job;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValFrame;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;
import water.rapids.ast.params.AstNumList;

/**
 * Quantiles:
 * (quantile %frame [numnber_list_probs] "string_interpolation_type")
 */
public class AstQtile extends AstPrimitive {
  @Override
  public String[] args() {
    return new String[]{"ary", "probs", "interpolationMethod", "weights_column"};
  }

  @Override
  public int nargs() {
    return 1 + 4;
  }

  @Override
  public String str() {
    return "quantile";
  }

  @Override
  public ValFrame apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame fr_wkey = new Frame(fr); // Force a bogus Key for Quantiles ModelBuilder
    DKV.put(fr_wkey);
    parms._train = fr_wkey._key;

    parms._probs = ((AstNumList) asts[2]).expand();
    for (double d : parms._probs)
      if (d < 0 || d > 1) throw new IllegalArgumentException("Probability must be between 0 and 1: " + d);

    String inter = asts[3].exec(env).getStr();
    parms._combine_method = QuantileModel.CombineMethod.valueOf(inter.toUpperCase());
    parms._weights_column = asts[4].str().equals("_") ? null : asts[4].str();

    // Compute Quantiles
    Job j = new Quantile(parms).trainModel();
    QuantileModel q = (QuantileModel) j.get();
    DKV.remove(j._key);

    // Remove bogus Key
    DKV.remove(fr_wkey._key);

    // Reshape all outputs as a Frame, with probs in col 0 and the
    // quantiles in cols 1 thru fr.numCols() - except the optional weights vec
    int ncols = fr.numCols();
    if (parms._weights_column != null) ncols--;
    Vec[] vecs = new Vec[1 /*1 more for the probs themselves*/ + ncols];
    String[] names = new String[vecs.length];
    vecs[0] = Vec.makeCon(null, parms._probs);
    names[0] = "Probs";
    int w = 0;
    for (int i = 0; i < vecs.length - 1; ++i) {
      if (fr._names[i].equals(parms._weights_column)) w = 1;
      vecs[i + 1] = Vec.makeCon(null, q._output._quantiles[i]);
      names[i + 1] = fr._names[w + i] + "Quantiles";
    }
    q.delete();

    return new ValFrame(new Frame(names, vecs));
  }
}

