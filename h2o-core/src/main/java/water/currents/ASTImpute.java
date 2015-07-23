package water.currents;

import hex.quantile.QuantileModel;
import java.util.Arrays;
import water.*;
import water.fvec.*;
import water.nbhm.*;

public class ASTImpute extends ASTPrim {
  @Override String str(){ return "h2o.impute";}
  @Override int nargs() { return 1+6; } // (h2o.impute data col method combine_method groupby in.place)
  private static enum ImputeMethod { MEAN , MEDIAN, MODE }
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // Argument parsing and sanity checking
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    double col = asts[2].exec(env).getNum();
    if( col < 0 || col >= fr.numCols() )
      throw new IllegalArgumentException("Column not in range 0 to "+fr.numCols());

    ImputeMethod method = ImputeMethod.valueOf(asts[3].exec(env).getStr().toUpperCase());

    QuantileModel.CombineMethod combine = QuantileModel.CombineMethod.valueOf(asts[4].exec(env).getStr().toUpperCase());

    // Group-by columns
    AST ast = asts[5];
    ASTNumList by;
    if( ast == null ) by = null;
    else if( ast instanceof ASTNumList  ) by = (ASTNumList)ast;
    else if( ast instanceof ASTNum ) by = new ASTNumList(((ASTNum)ast)._d.getNum());
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());

    final boolean inplace = asts[6].exec(env).getNum() == 1;
    if( inplace && fr._key==null )
      throw new IllegalArgumentException("Can only update in-place named Frames");


    throw H2O.unimpl();
  }
}
