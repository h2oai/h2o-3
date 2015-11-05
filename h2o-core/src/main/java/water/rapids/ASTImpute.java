package water.rapids;

import hex.quantile.QuantileModel;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.IcedDouble;
import water.util.IcedHashMap;

import java.util.Arrays;

// (h2o.impute data col method combine_method gb in.place)

public class ASTImpute extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "col", "method", "combineMethod", "groupByCols"}; }
  @Override public String str(){ return "h2o.impute";}
  @Override int nargs() { return 1+5; } // (h2o.impute data col method combine_method groupby)
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // Argument parsing and sanity checking
    // Whole frame being imputed
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Column within frame being imputed
    final int col = (int)asts[2].exec(env).getNum();
    if( col < 0 || col >= fr.numCols() )
      throw new IllegalArgumentException("Column not in range 0 to "+fr.numCols());
    final Vec vec = fr.vec(col);

    // Technique used for imputation
    AST method;
    switch( asts[3].exec(env).getStr().toUpperCase() ) {
    case "MEAN"  : method = new ASTMean  (); break;
    case "MEDIAN": method = new ASTMedian(); break;
    case "MODE"  : method = new ASTMode  (); break;
    default: throw new IllegalArgumentException("Method must be one of mean, median or mode");
    }

    // Only for median, how is the median computed on even sample sizes?
    QuantileModel.CombineMethod combine = QuantileModel.CombineMethod.valueOf(asts[4].exec(env).getStr().toUpperCase());

    // Group-by columns.  Empty is allowed, and perfectly normal.
    AST ast = asts[5];
    ASTNumList by2;
    if( ast instanceof ASTNumList  ) by2 = (ASTNumList)ast;
    else if( ast instanceof ASTNum ) by2 = new ASTNumList(((ASTNum)ast)._v.getNum());
    else if( ast instanceof ASTStrList ) {
      String[] names = ((ASTStrList)ast)._strs;
      double[] list  = new double[names.length];
      int i=0;
      for( String name: ((ASTStrList)ast)._strs)
        list[i++]=fr.find(name);
      Arrays.sort(list);
      by2 = new ASTNumList(list);
    }
    else throw new IllegalArgumentException("Requires a number-list, but found a "+ast.getClass());
    final ASTNumList by = by2;  // Make final, for MRTask closure

    // Compute the imputed value per-group.  Empty groups are allowed and OK.
    IcedHashMap<ASTGroup.G,IcedDouble> group_impute_map;
    if( by.isEmpty() ) {        // Empty group?  Skip the grouping work
      double res = Double.NaN;
      if( method instanceof ASTMean   ) res = vec.mean();
      if( method instanceof ASTMedian ) res = ASTMedian.median(new Frame(vec), combine);
      if( method instanceof ASTMode   ) res = ASTMode.mode(vec);
      (group_impute_map = new IcedHashMap<>()).put(new ASTGroup.G(0,null).fill(0,null,new int[0]),new IcedDouble(res));

    } else {                    // Grouping!
      // Build and run a GroupBy command
      AST ast_grp = new ASTGroup();
      Frame imputes = ast_grp.apply(env,stk,new AST[]{ast_grp,new ASTFrame(fr),by,new ASTNumList(),method,new ASTNumList(col,col+1),new ASTStr("rm")}).getFrame();
     
      // Convert the Frame result to a group/imputation mapping
      final int[] bycols = ArrayUtils.seq(0, imputes.numCols() - 1);
      group_impute_map = new Gather(bycols).doAll(imputes)._group_impute_map;
      imputes.delete();
    }

    // Copy the target column as needed
    env._ses.copyOnWrite(fr,new int[]{col});

    // Now walk over the data, replace NAs with the imputed results
    final IcedHashMap<ASTGroup.G,IcedDouble> final_group_impute_map = group_impute_map;
    final int[] bycols = by.expand4();
    new MRTask() {
      @Override public void map( Chunk cs[] ) {
        Chunk x = cs[col];
        ASTGroup.G g = new ASTGroup.G(bycols.length,null);
        for( int row=0; row<x._len; row++ )
          if( x.isNA(row) )
            x.set(row,final_group_impute_map.get(g.fill(row,cs,bycols))._val);
      }
    }.doAll(fr);

    return new ValFrame(fr);
  }

  private static class Gather extends MRTask<Gather> {
    private final int[] _bycols;
    private IcedHashMap<ASTGroup.G,IcedDouble> _group_impute_map;
    Gather( int[] bycols ) { _bycols = bycols; }
    @Override public void map( Chunk cs[] ) {
      _group_impute_map = new IcedHashMap<>();
      Chunk means = cs[cs.length-1]; // Imputed value is last in the frame
      for( int i=0; i<cs[0]._len; i++ ) // For all groups
        _group_impute_map.put(new ASTGroup.G(cs.length-1,null).fill(i,cs,_bycols),new IcedDouble(means.atd(i)));
    }
    @Override public void reduce( Gather mrt ) { _group_impute_map.putAll(mrt._group_impute_map); }
  }
}
