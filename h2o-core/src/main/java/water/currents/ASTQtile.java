package water.currents;

import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.DKV;
import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Quantiles: 
 *  (quantile %frame [numnber_list_probs] "string_interpolation_type")
 */
class ASTQtile extends ASTPrim {
  @Override int nargs() { return 1+3; }
  @Override String str() { return "quantile"; }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Frame fr_wkey = new Frame(fr); // Force a bogus Key for Quantiles ModelBuilder
    DKV.put(fr_wkey);
    parms._train = fr_wkey._key;

    parms._probs = ((ASTNumList)asts[2]).expand();
    for( double d : parms._probs )
      if( d < 0 || d > 1 ) throw new IllegalArgumentException("Probability must be between 0 and 1: "+d);

    String inter = asts[3].exec(env).getStr();
    parms._combine_method = QuantileModel.CombineMethod.valueOf(inter.toUpperCase());

    // Compute Quantiles
    QuantileModel q = new Quantile(parms).trainModel().get();

    // Remove bogus Key
    DKV.remove(fr_wkey._key);

    // Reshape all outputs as a Frame, with probs in col 0 and the
    // quantiles in cols 1 thru fr.numCols()
    Vec[] vecs = new Vec[1 /*1 more for the probs themselves*/ +fr.numCols()];
    String[] names = new String[vecs.length];
    vecs [0] = Vec.makeCon(null,parms._probs);
    names[0] = "Probs";
    for( int i=0; i<fr.numCols(); ++i ) {
      vecs [i+1] = Vec.makeCon(null,q._output._quantiles[i]);
      names[i+1] = fr._names[i]+"Quantiles";
    }
    q.delete();

    return new ValFrame(new Frame(names,vecs));
  }
}

