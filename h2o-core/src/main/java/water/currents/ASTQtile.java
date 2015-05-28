package water.currents;

import water.H2O;
import water.parser.ValueString;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import water.fvec.Frame;

/**
 * Quantiles: 
 *  (quantile %frame [numnber_list_probs] "string_interpolation_type")
 */
class ASTQtile extends ASTPrim {
  @Override int nargs() { return 1+3; }
  @Override String str() { return "quantile"; }
  @Override Val apply( Env env, AST asts[] ) {
    try (Env.StackHelp stk = env.stk()) {
        Frame fr = stk.track(asts[1].exec(env)).getFrame();
        ASTNumList probs = ((ASTNumList)asts[2]);
        


        String inter = asts[3].exec(env).getStr();
        QuantileModel.CombineMethod combine_method = QuantileModel.CombineMethod.valueOf(inter.toUpperCase());
        
        throw H2O.unimpl();
      }
  }
}

