package water.rapids;

import hex.quantile.QuantileModel;
import water.H2O;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.IcedDouble;
import water.util.IcedHashMap;

import java.util.Arrays;

/**
 * Impute columns of a data frame in place.
 *
 * This impute can impute whole Frames or a specific Vec within the Frame. Imputation
 * will be by the default mean (for numeric columns) or mode (for categorical columns).
 * String, date, and UUID columns are never imputed.
 *
 * When a Vec is specified to be imputed, it can alternatively be imputed by grouping on
 * some other columns in the Frame. If groupByCols is specified, but the user does not
 * supply a column to be imputed then an IllegalArgumentException will be raised. Further,
 * if the user specifies the column to impute within the groupByCols, exceptions will be
 * raised.
 *
 * The methods that a user may impute by are as follows:
 *  - mean: Vec.T_NUM
 *  - median: Vec.T_NUM
 *  - mode: Vec.T_CAT
 *  - bfill: Any valid Vec type
 *  - ffill: Any valid Vec type
 *
 * All methods of imputation are done in place! The first three methods (mean, median,
 * mode) are self-explanatory. The bfill and ffill methods will attempt to fill NAs using
 * adjacent cell value (either before or forward):
 *
 *               Vec = [ bfill_value, NA, ffill_value]
 *                           |        ^^       |
 *                           ->       ||      <-
 *                                  impute
 *
 * If the impute method is median then the combineMethod can be one of the Enum variants
 * of QuantileModel.CombineMethod = { INTERPOLATE, AVERAGE, LOW, HIGH }. The Enum
 * specifies how to combine quantiles on even sample sizes. This parameter is ignored in
 * all other cases.
 *
 * Finally, the groupByFrame can be used to impute a column with a pre-computed groupby
 * result.
 *
 * Other notes:
 *
 * If col is -1, then the entire Frame will be imputed using mean/mode where appropriate.
 */
public class ASTImpute extends ASTPrim {
  @Override public String[] args() { return new String[]{"ary", "col", "method", "combineMethod", "groupByCols", "groupByFrame", "values"}; }
  @Override public String str(){ return "h2o.impute";}
  @Override int nargs() { return 1+7; } // (h2o.impute data col method combine_method groupby groupByFrame values)
  @Override Val apply( Env env, Env.StackHelp stk, AST asts[] ) {
    // Argument parsing and sanity checking
    // Whole frame being imputed
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Column within frame being imputed
    final int col = (int)asts[2].exec(env).getNum();
    if( col >= fr.numCols() )
      throw new IllegalArgumentException("Column not in range 0 to "+fr.numCols());
    final boolean doAllVecs = col==-1;
    final Vec vec = doAllVecs?null:fr.vec(col);

    // Technique used for imputation
    AST method=null;
    boolean ffill0=false;
    boolean bfill0=false;
    switch( asts[3].exec(env).getStr().toUpperCase() ) {
      case "MEAN"  : method = new ASTMean  (); break;
      case "MEDIAN": method = new ASTMedian(); break;
      case "MODE"  : method = new ASTMode  (); break;
      case "FFILL" : ffill0  = true; break;
      case "BFILL" : bfill0  = true; break;
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

    Frame groupByFrame = asts[6].str().equals("_")?null:stk.track(asts[6].exec(env)).getFrame();
    AST vals = asts[7];
    ASTNumList values;
    if( vals instanceof ASTNumList  ) values = (ASTNumList)vals;
    else if( vals instanceof ASTNum ) values = new ASTNumList(((ASTNum)vals)._v.getNum());
    else values=null;

    ASTNumList by = by2;  // Make final, for MRTask closure

    boolean doGrpBy = !by.isEmpty() || groupByFrame!=null;
    // Compute the imputed value per-group.  Empty groups are allowed and OK.
    IcedHashMap<ASTGroup.G,IcedDouble> group_impute_map;
    if( !doGrpBy ) {        // Skip the grouping work
      if( ffill0 || bfill0 ) {  // do a forward/backward fill on the NA
        // TODO: requires chk.previousNonNA and chk.nextNonNA style methods (which may go across chk boundaries)s
        final boolean ffill=ffill0;
        final boolean bfill=bfill0;
        throw H2O.unimpl("No ffill or bfill imputation supported");
//        new MRTask() {
//          @Override public void map(Chunk[] cs) {
//            int len=cs[0]._len; // end of this chk
//            long start=cs[0].start();  // absolute beginning of chk s.t. start-1 bleeds into previous chk
//            long absEnd = start+len;   // absolute end of the chk s.t. absEnd+1 bleeds into next chk
//            for(int c=0;c<cs.length;++c )
//              for(int r=0;r<cs[0]._len;++r ) {
//                if( cs[c].isNA(r) ) {
//                  if( r > 0 && r < len-1 ) {
//                    cs[c].set(r,ffill?)
//                  }
//                }
//              }
//          }
//        }.doAll(doAllVecs?fr:new Frame(vec));
//        return new ValNum(Double.NaN);
      } else {
        final double[] res = values==null?new double[fr.numCols()]:values.expand();
        if( values==null ) { // fill up res if no values supplied user, common case
          if (doAllVecs) {
            for (int i = 0; i < res.length; ++i)
              if (fr.vec(i).isNumeric() || fr.vec(i).isCategorical())
                res[i] = fr.vec(i).isNumeric() ? fr.vec(i).mean() : ArrayUtils.maxIndex(fr.vec(i).bins());
          } else {
            Arrays.fill(res, Double.NaN);
            if (method instanceof ASTMean) res[col] = vec.mean();
            if (method instanceof ASTMedian)
              res[col] = ASTMedian.median(new Frame(vec), combine);
            if (method instanceof ASTMode) res[col] = ASTMode.mode(vec);
          }
        }
        new MRTask() {
          @Override
          public void map(Chunk[] cs) {
            int len = cs[0]._len;
            // run down each chk
            for (int c = 0; c < cs.length; ++c)
              if (!Double.isNaN(res[c]))
                for (int row = 0; row < len; ++row)
                  if (cs[c].isNA(row))
                    cs[c].set(row, res[c]);
          }
        }.doAll(fr);
        return new ValNums(res);
      }
    } else {
      if( col < 0 || col >= fr.numCols() )
        throw new IllegalArgumentException("Column not in range 0 to "+fr.numCols());
      Frame imputes=groupByFrame;
      if( imputes==null ) {
        // Build and run a GroupBy command
        AST ast_grp = new ASTGroup();
        imputes = ast_grp.apply(env,stk,new AST[]{ast_grp,new ASTFrame(fr),by,method,new ASTNumList(col,col+1),new ASTStr("rm")}).getFrame();
      }
      final int[] bycols0 = ArrayUtils.seq(0, imputes.numCols() - 1);
      group_impute_map = new Gather(bycols0).doAll(imputes)._group_impute_map;

      // Now walk over the data, replace NAs with the imputed results
      final IcedHashMap<ASTGroup.G,IcedDouble> final_group_impute_map = group_impute_map;
      if( by.isEmpty() ) {
        int[] byCols = new int[imputes.numCols()-1];
        for(int i=0;i<byCols.length;++i)
          byCols[i] = fr.find(imputes.name(i));
        by = new ASTNumList(byCols);
      }
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
      return new ValFrame(imputes);
    }
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
