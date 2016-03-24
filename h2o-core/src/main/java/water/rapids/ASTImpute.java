package water.rapids;

import hex.quantile.QuantileModel;
import water.Freezable;
import water.H2O;
import water.Iced;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.IcedDouble;
import water.util.IcedHashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
  @Override
  public Val apply(Env env, Env.StackHelp stk, AST asts[]) {
    // Argument parsing and sanity checking
    // Whole frame being imputed
    Frame fr = stk.track(asts[1].exec(env)).getFrame();

    // Column within frame being imputed
    final int col = (int)asts[2].exec(env).getNum();
    if( col >= fr.numCols() )
      throw new IllegalArgumentException("Column not -1 or in range 0 to "+fr.numCols());
    final boolean doAllVecs = col==-1;
    final Vec vec = doAllVecs?null:fr.vec(col);

    // Technique used for imputation
    AST method=null;
    boolean ffill0 = false, bfill0 = false;
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
    boolean doGrpBy = !by2.isEmpty() || groupByFrame!=null;
    // Compute the imputed value per-group.  Empty groups are allowed and OK.
    IcedHashMap<ASTGroup.G,Freezable[]> group_impute_map;
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
      if( col >= fr.numCols() )
        throw new IllegalArgumentException("Column not -1 or in range 0 to "+fr.numCols());
      Frame imputes=groupByFrame;
      if( imputes==null ) {
        // Build and run a GroupBy command
        AST ast_grp = new ASTGroup();

        // simple case where user specified a column... col == -1 means do all columns
        if( doAllVecs ) {
          AST[] aggs = new AST[(int)(3+3*(fr.numCols()-by2.cnt()))];
          aggs[0] = ast_grp;
          aggs[1] = new ASTFrame(fr);
          aggs[2] = by2;
          int c=3;
          for(int i=0;i<fr.numCols();++i) {
            if( !by2.has(i) && (fr.vec(i).isCategorical() || fr.vec(i).isNumeric()) ) {
              aggs[c] = fr.vec(i).isNumeric() ? new ASTMean() : new ASTMode();
              aggs[c+1] = new ASTNumList(i,i+1);
              aggs[c+2] = new ASTStr("rm");
              c+=3;
            }
          }
          imputes = ast_grp.apply(env,stk,aggs).getFrame();
        } else
          imputes = ast_grp.apply(env, stk, new AST[]{ast_grp, new ASTFrame(fr), by2,  /**/method, new ASTNumList(col, col + 1), new ASTStr("rm") /**/}).getFrame();
      }
      if( by2.isEmpty() && imputes.numCols() > 2 ) // >2 makes it ambiguous which columns are groupby cols and which are aggs, throw IAE
        throw new IllegalArgumentException("Ambiguous group-by frame. Supply the `by` columns to proceed.");

      final int[] bycols0 = ArrayUtils.seq(0, Math.max((int)by2.cnt(), 1 /* imputes.numCols()-1 */));
      group_impute_map = new Gather(by2.expand4(),bycols0,fr.numCols(),col).doAll(imputes)._group_impute_map;

      // Now walk over the data, replace NAs with the imputed results
      final IcedHashMap<ASTGroup.G, Freezable[]> final_group_impute_map = group_impute_map;
      if( by2.isEmpty() ) {
        int[] byCols = new int[imputes.numCols()-1];
        for(int i=0;i<byCols.length;++i)
          byCols[i] = fr.find(imputes.name(i));
        by2 = new ASTNumList(byCols);
      }
      final int[] bycols = by2.expand4();
      new MRTask() {
        @Override public void map( Chunk cs[] ) {
          Set<Integer> _bycolz =  new HashSet<>();
          for(int b:bycols) _bycolz.add(b);
          ASTGroup.G g = new ASTGroup.G(bycols.length,null);
          for( int row=0; row<cs[0]._len; row++ )
            for(int c=0;c<cs.length;++c)
              if (!_bycolz.contains(c))
                if( cs[c].isNA(row) )
                  cs[c].set(row, ((IcedDouble)final_group_impute_map.get(g.fill(row, cs, bycols))[c])._val);
        }
      }.doAll(fr);
      return new ValFrame(imputes);
    }
  }

  // flatten the GroupBy result Frame back into a IcedHashMap
  private static class Gather extends MRTask<Gather> {
    private final int _imputedCol;
    private final int _ncol;
    private final int[] _byCols0; // actual group-by indexes
    private final int[] _byCols;  // index into the grouped-by frame result
    private IcedHashMap<ASTGroup.G,Freezable[]> _group_impute_map;
    private transient Set<Integer> _localbyColzSet;
    Gather( int[] byCols0, int[] byCols, int ncol, int imputeCol) { _byCols=byCols; _byCols0=byCols0; _ncol=ncol; _imputedCol=imputeCol; }
    @Override public void setupLocal() { _localbyColzSet=new HashSet<>(); for(int by: _byCols0) _localbyColzSet.add(by); }
    @Override public void map(Chunk cs[]) {
      _group_impute_map = new IcedHashMap<>();
      for(int row=0; row<cs[0]._len; ++row) {
        IcedDouble[] imputes = new IcedDouble[_ncol];
        for(int c=0, z=_byCols.length;c<imputes.length;++c,++z) {  // z used to skip over the gby cols into the columns containing the aggregated columns
          if( _imputedCol!=-1 ) imputes[c] = c==_imputedCol?new IcedDouble(cs[cs.length-1].atd(row)):new IcedDouble(Double.NaN);
          else                  imputes[c] = _localbyColzSet.contains(c) ? new IcedDouble(Double.NaN) : new IcedDouble(cs[z].atd(row));
        }
        _group_impute_map.put(new ASTGroup.G(_byCols.length,null).fill(row,cs,_byCols), imputes);
      }
    }
    @Override public void reduce( Gather mrt ) { _group_impute_map.putAll(mrt._group_impute_map); }
  }
}
