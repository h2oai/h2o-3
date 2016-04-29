package ai.h2o.automl.transforms;

import ai.h2o.automl.FrameMeta;
import hex.Model;
import water.DKV;
import water.H2O;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.ASTGroup;
import water.util.IcedHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/** Automatic Synthesis of Features
 *
 * Create new features from an existing Frame by applying per-row ops to collections
 * of features or collections of aggregated features. For example, it may be interesting
 * to take the log of a single column, or add two columns together to produce a third. It
 * may be interesting to aggregate values for groups of data and then apply some type of
 * per-row op. For example, it may be useful to collect sums of columns of grouped data.
 *
 *
 * For a set of predictors X = {X_1, ... , X_p}, a new feature f is a function defined by
 *
 *          F : X -> x
 *
 * It's a bit of an abuse of notation, but the meaning should be clear. F is a family of
 * functions that take the predictors as input and produce a single new column. A feature
 * produced by a function from F is given by:
 *
 *    f = fun(X_1, ..., X_p)
 *
 *
 * In the case where no aggregate is performed, there is an interesting optimization that
 * allows the newly minted feature to never fully materialize. For this case we use
 * TransformWrappedVec instances in which the 'atd' call performs the actions specified
 * by fun in an AST.
 *
 *
 *
 * Features are generated and pruned in straightforward ways.
 *
 *
 *  First cut means no smarts in the generation process, just generate features and see
 *  what sticks.
 *
 * Generating Features:
 *
 *  Because we want to have as little human involvement as possible, we are left with what
 *  is essentially a grid search. At the point of feature-generation, it is assumed that
 *  any Frame pre-processing (scaling, imputing, sampling) is already complete. In the case
 *  of generating a feature from one column, that column selects a transformation (e.g.
 *  log, sqrt) MOST befitting its type (T_BAD, T_UUID, T_STR, T_NUM, T_CAT, T_TIME) and
 *  any other meta information (e.g. take a log because the column spans many orders of
 *  mag).
 *
 *  We allow the Frame to decide how to combine two or more features will be combined so
 *  as to avoid duplication of features.
 *
 *  Aggregations are currently only allowed on categorical/integer columns. Collections of
 *  categorical/integer columns can be used as keys, and each new group-by key requires a
 *  real pass over the data. Since we have to pay the price of going over the data, and
 *  because we have to pay the price of looking up the aggregate when materializing the
 *  feature (essentially the resulting group-by table must fit on single node, ouch!), we
 *  put some hard limits on the number of groups (1 million) above which aggregation-based
 *  feature generation is abandoned.
 *
 *  Since ops can be stacked arbitrarily high, it will be useful to limit how "tall" any
 *  TransformWrappedVec can become. We arbitrarily set the composition height to 3, and
 *  only allow further feature composition once there has been sufficient exploration at
 *  shorter compositions.
 *
 * Pruning Features:
 *  If the generated features aren't producing good results (as told by the first few
 *  trees of a GBM, e.g.), then immediately abandon the model build and prune.
 *
 *  Since we want to verify the new features without paying too much for a few trees of a
 *  GBM, we may want to try some smart sampling strategy to reduce the size of the
 *  training data. But, this isn't the concern of the pruning logic since it's assumed
 *  that any data pre-processing (scaling, imputing, sampling) has taken place.
 *
 *
 * Approach 2:
 *  A second approach here could be that a few hundred features are generated all at once
 *  and those with "good" values in variable importance are kept and all others pruned.
 *
 * Storing Features:
 *  Useful features are tracked for purposes of providing more information to the data
 *  scientist user.
 *
 *
 * Some heuristics:
 *    Allowing users to do a log(), or log1p() quickly would be nice.
 *    Trying it behind their back might be useful. I've started to rattle off
 *        "if your numbers span more than an order of magnitude,x
 *         you might try a log transform"
 *    quite often. Reasonable enough heuristic. Heuristics are good for DSiaB.
 *
 *   Hard-coding a few common things to look for: "Year" is an integer, but log
 *   transforming it likely doesn't make as much sense; special transforms exist
 *   for that, so we might want to understand the difference and be willing to guess.
 */
public class FeatureFactory {

  private final FrameMeta _fm;             // the input frame

  private HashMap<String,Expr> _basicTransforms;
  private Frame _basicTransformFrame;
  private Frame _catInteractions;
  private final String _response;
  private final String[] _predictors;
  private final boolean _isClassification;


  /**
   * Create a new FeatureFactory for this Frame
   * @param f Frame upon which to generate new features
   */
  public FeatureFactory(Frame f, String[] predictors, String response, boolean isClassification) {
    _predictors=predictors;
    _response=response;
    _isClassification=isClassification;
    _fm = new FrameMeta(f,f.find(response),predictors,f._key.toString(),isClassification);
  }

  public void delete() {
    if(null!=_basicTransformFrame)_basicTransformFrame.delete();
    if(null!=_catInteractions)    _catInteractions.delete();
  }

  /**
   * Build some very basic transforms by looking for rates, taking logs, and doing
   * simple binary addition of features
   */
  public void synthesizeBasicFeatures() {
    // generate min(nnum*nnum,200) features
    int nBasicFeats = Math.min(_fm.numberOfNumericFeatures()*_fm.numberOfNumericFeatures(),200);
    if( _fm.numberOfNumericFeatures() > 0) {
      _basicTransforms = new HashMap<>();
      String op;
      int cnt=0;
      for(int i=0;i<_fm._numFeats.length;++i) {
        String name = _fm._cols[_fm._numFeats[i]]._name + "_" + (op=_fm._cols[_fm._numFeats[i]].selectBasicTransform());
        if(op.equals("ignore") || op.equals("none")) continue;
        if( op.equals("time")) throw H2O.unimpl("does not generate time-based ops yet...");
        _basicTransforms.put(name, Expr.unOp(op,_fm._cols[_fm._numFeats[i]]._v));
        cnt++;
      }
      cnt += tryRatesAndReciprocals(nBasicFeats - cnt);
      cnt += tryCombos(nBasicFeats-cnt);
      // cnt += tryTime(nBasicFeats-cnt);
      cnt += tryCatInteractions(nBasicFeats-cnt);
      generateBasicTransformFrame();
    }
  }

  private void generateBasicTransformFrame() {
    int sz;
    if( (sz=_basicTransforms.size()) > 0) {
      String[] names = _basicTransforms.keySet().toArray(new String[sz]);
      Vec[] tVecs = new Vec[sz];
      int i=0;
      for(String n: names)
        tVecs[i++] = _basicTransforms.get(n).toWrappedVec();
      _basicTransformFrame = new Frame(Key.<Frame>make("basicTransFrame" + Key.make().toString()),names, tVecs);
      DKV.put(_basicTransformFrame);
    }
  }

  /**
   * Choose all keys to group-by up to 200 resulting aggregate columns
   * that will be joining back to the Frame.
   *
   *
   * want to compute these types of aggregates:
   *    mean, sum, var, count
   *
   *  over the original columns + any basic synthesize features
   *  Can do a single pass to compute all group-by results (for all groupings).
   *
   * @return
   */
  public void synthesizeAggFeatures() {
    HashSet<String> preds = new HashSet<>();
    Collections.addAll(preds,_predictors);
    Frame fullFrame = new Frame(_fm._fr.names().clone(), _fm._fr.vecs().clone());
    if( null!=_basicTransformFrame) {
      Collections.addAll(preds,_basicTransformFrame.names());
      fullFrame.add(_basicTransformFrame);
    }
    if( null!=_catInteractions )  {
      Collections.addAll(preds, _catInteractions.names());
      fullFrame.add(_catInteractions);
    }
    FrameMeta fullFrameMeta = new FrameMeta(fullFrame,fullFrame.find(_response),preds.toArray(new String[preds.size()]), "synthesized_fullFrame",_isClassification);
    AggFeatureBuilder afb = AggFeatureBuilder.buildAggFeatures(fullFrameMeta);
    GBTasks gbs = new GBTasks(afb._gbCols,afb._aggs);
    gbs.doAll(fullFrame);
    System.out.println();
  }

  /**
   * A dummy class to hold all group-by combos plus any desired aggregates
   */
  public static class AggFeatureBuilder {
    public final int[][] _gbCols;
    public final ASTGroup.AGG[][] _aggs;
    public AggFeatureBuilder(int[][] gbCols, ASTGroup.AGG[][] aggs) { _gbCols=gbCols; _aggs=aggs; }

    public static AggFeatureBuilder buildAggFeatures(FrameMeta fullFrameMeta) {
      // choose group-by keys:
      //  1. must be categorical
      //  2. must have combined total-cardinality < 1M
      //  3. at most 5 cols per key
      //     => and then all combinations therein
      // AGG fcns:
      ArrayList<ASTGroup.AGG[]> agg = new ArrayList<>();
      ArrayList<Integer[]> gbCols = new ArrayList<>();

      fullFrameMeta.numberOfCategoricalFeatures();
      int[] catFeats = fullFrameMeta._catFeats;
      for(int i=0;i<catFeats.length;++i) {                                                  //        1 key group
        gbCols.add(new Integer[]{catFeats[i]});
        agg.add(makeAggs(new int[]{catFeats[i]}, fullFrameMeta));
        for(int j=i+1;j<catFeats.length; ++j) {                                             //        2 key group
          gbCols.add(new Integer[]{catFeats[i],catFeats[j]});
          agg.add(makeAggs(new int[]{catFeats[i],catFeats[j]},fullFrameMeta));
          for(int k=j+1;k<fullFrameMeta._catFeats.length;++k) {                             //        3 key group
            gbCols.add(new Integer[]{catFeats[i],catFeats[j],catFeats[k]});
            agg.add(makeAggs(new int[]{catFeats[i],catFeats[j],catFeats[k]},fullFrameMeta));
            for(int l=k+1;l<fullFrameMeta._catFeats.length;++l) {                           //        4 key group
              gbCols.add(new Integer[]{catFeats[i],catFeats[j],catFeats[k],catFeats[l]});
              agg.add(makeAggs(new int[]{catFeats[i],catFeats[j],catFeats[k],catFeats[l]},fullFrameMeta));
            }
          }
        }
      }
      int[][] groupByCols = new int[gbCols.size()][];
      ASTGroup.AGG[][] aggs = new ASTGroup.AGG[agg.size()][];

      // could launch all group-by tasks in parallel => BIG memory overhead EEKS!
      for(int i=0;i<groupByCols.length;++i) {
        groupByCols[i] = new int[gbCols.get(i).length];
        for(int j=0;j<groupByCols[i].length;++j)
          groupByCols[i][j] = gbCols.get(i)[j];
        aggs[i] = agg.get(i);
      }
      return new AggFeatureBuilder(groupByCols,aggs);
    }
  }

  private static ASTGroup.AGG[] makeAggs(int[] gbCols, FrameMeta fm) {
    // numerical columns get sum, mean, var
    ArrayList<ASTGroup.AGG> aggs = new ArrayList<>();
    int[] cols = fm.diffCols(gbCols);
    for (int col : cols)
      if (fm._fr.vec(col).isNumeric()) {
        aggs.add(new ASTGroup.AGG(ASTGroup.FCN.mean, col, ASTGroup.NAHandling.IGNORE, -1));
        aggs.add(new ASTGroup.AGG(ASTGroup.FCN.var,  col, ASTGroup.NAHandling.IGNORE, -1));
        aggs.add(new ASTGroup.AGG(ASTGroup.FCN.sum,  col, ASTGroup.NAHandling.IGNORE, -1));
      }
    return aggs.toArray(new ASTGroup.AGG[aggs.size()]);
  }



  /**
   * Try to find rates by dividing numerical columns by integral columns.
   *
   * Find dblCols and divide by all integer non binary cols (fudge the denom by eps)
   *
   * Try to sum up double cols and then divide by all integer cols
   *
   * TODO: misses the case where we want to find rates of integer cols
   */
  private int tryRatesAndReciprocals(int maxFeats) {
    int cnt=0;
    for(int i: _fm._dblCols) {
      for(int j: _fm._intNotBinaryCols) {
        if( cnt >= maxFeats ) return cnt;
        String name = _fm._cols[i]._name + "_rate_" + _fm._cols[j]._name;
        _basicTransforms.put(name, Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v));
        name = _fm._cols[i]._name + "_recip_" + _fm._cols[j]._name;
        _basicTransforms.put(name, Expr.binOp("/",1,Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v)));
        cnt+=2;
      }
    }

    // now try to do all the pairwise additions of double columns divided by every int col
    for(int i=0;i<_fm._dblCols.length;++i)
      for(int j=i+1;j<_fm._dblCols.length;++j)
        for(int k: _fm._intNotBinaryCols) {
          if( cnt >= maxFeats ) return cnt;
          String name = _fm._cols[i]._name + "_plus_" + _fm._cols[j]._name + "_rate_" + _fm._cols[k]._v;
          _basicTransforms.put(name, Expr.binOp("+",_fm._cols[i]._v,_fm._cols[j]._v).bop("/",_fm._cols[k]._v));
          name = _fm._cols[i]._name + "_plus_" + _fm._cols[j]._name + "_recip" + _fm._cols[k]._v;
          _basicTransforms.put(name, Expr.binOp("/",1,Expr.binOp("+",_fm._cols[i]._v,_fm._cols[j]._v).bop("/", _fm._cols[k]._v)));
          cnt+=2;
        }

    // now with all combinations of three double vecs
    for(int i=0;i<_fm._dblCols.length;++i)
      for(int j=i+1;j<_fm._dblCols.length;++j)
        for(int k=j+1;k<_fm._dblCols.length;++k)
          for(int l: _fm._intNotBinaryCols) {
            if( cnt >= maxFeats ) return cnt;
            String name = _fm._cols[i]._name + "_plus_" + _fm._cols[j]._name + _fm._cols[k]._name + "_rate_" + _fm._cols[l]._v;
            _basicTransforms.put(name, Expr.binOp("+",_fm._cols[i]._v,_fm._cols[j]._v).bop("+",_fm._cols[k]._v).bop("/",_fm._cols[l]._v));
            name = _fm._cols[i]._name + "_plus_" + _fm._cols[j]._name + _fm._cols[k]._name + "_recip_" + _fm._cols[l]._v;
            _basicTransforms.put(name, Expr.binOp("/",1,Expr.binOp("+",_fm._cols[i]._v,_fm._cols[j]._v).bop("+",_fm._cols[k]._v).bop("/",_fm._cols[l]._v)));
            cnt+=2;
          }

    // now all the integer (non binary) columns with each other:
    for(int i=0;i<_fm._intNotBinaryCols.length;++i)
      for(int j=i+1;j<_fm._intNotBinaryCols.length;++j) {
        String name = _fm._cols[i]._name + "_rate_" + _fm._cols[j]._name;
        _basicTransforms.put(name, Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v));
        name = _fm._cols[i]._name + "_recip_" + _fm._cols[j]._name;
        _basicTransforms.put(name, Expr.binOp("/",1,Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v)));
        cnt+=2;
      }
    return cnt;
  }

  private int tryCombos(int maxFeats) {
    int cnt=0;
    String[] transforms = _basicTransforms.keySet().toArray(new String[_basicTransforms.size()]);
    for(int i=0;i<transforms.length;++i) {
      for(int j=i+1;j<transforms.length;++j) {
        if( cnt >= maxFeats ) return cnt;
        String name = transforms[i] + "_plus_" + transforms[j];
        _basicTransforms.put(name, _basicTransforms.get(transforms[i]).bop("+", _basicTransforms.get(transforms[j])));
        cnt++;
      }
    }
    return cnt;
  }

  private int tryCatInteractions(int maxFeats) {
    int cnt=0;
    if( cnt >= maxFeats ) return cnt;
    _fm.numberOfCategoricalFeatures();
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(_fm._catFeats);
    _catInteractions = Model.makeInteractions(_fm._fr, false, ips, true,false,false);
    cnt += _catInteractions.numCols();
    return cnt;
  }


  private static class GBTasks extends MRTask<GBTasks> {
    final IcedHashMap<ASTGroup.G,String> _gss[]; // Shared per-node, common, racy
    private final int[][] _gbCols; // Columns used to define group
    private final ASTGroup.AGG[][] _aggs;   // Aggregate descriptions
    GBTasks(int[][] gbCols, ASTGroup.AGG[][] aggs) { _gbCols=gbCols; _aggs=aggs; _gss = new IcedHashMap[_gbCols.length]; for(int i=0;i<_gss.length;++i) _gss[i]=new IcedHashMap<>(); }
    @Override public void map(Chunk[] cs) {
      // Groups found in this Chunk
      IcedHashMap<ASTGroup.G,String> gs[] = new IcedHashMap[_gbCols.length];
      for(int gId=0;gId<_gbCols.length;++gId) {
        gs[gId] = new IcedHashMap<>();
        ASTGroup.G gWork = new ASTGroup.G(_gbCols[gId].length,_aggs[gId]);
        ASTGroup.G gOld;
        for( int row=0; row<cs[0]._len; row++ ) {
          // Find the Group being worked on
          gWork.fill(row,cs,_gbCols[gId]);            // Fill the worker Group for the hashtable lookup
          if( gs[gId].putIfAbsent(gWork,"")==null ) { // Insert if not absent (note: no race, no need for atomic)
            gOld=gWork;                          // Inserted 'gWork' into table
            gWork=new ASTGroup.G(_gbCols[gId].length,_aggs[gId]);   // need entirely new G
          } else gOld=gs[gId].getk(gWork);            // Else get existing group

          for( int i=0; i<_aggs[gId].length; i++ ) // Accumulate aggregate reductions
            _aggs[gId][i].op(gOld._dss,gOld._ns,i, cs[_aggs[gId][i]._col].atd(row));
        }
      }
      reduce(gs);               // Atomically merge Group stats
    }
    // Racy update on a subtle path: reduction is always single-threaded, but
    // the shared global hashtable being reduced into is ALSO being written by
    // parallel map calls.
    @Override public void reduce(GBTasks t) { if( _gss != t._gss ) reduce(t._gss); }
    // Non-blocking race-safe update of the shared per-node groups hashtable
    private void reduce(IcedHashMap<ASTGroup.G,String>[] r) {
      for(int gId=0;gId<r.length;++gId)
        reduce(r[gId],gId);
    }
    private void reduce( IcedHashMap<ASTGroup.G,String> r, int gID ) {
      for( ASTGroup.G rg : r.keySet() )
        if( _gss[gID].putIfAbsent(rg,"")!=null ) {
          ASTGroup.G lg = _gss[gID].getk(rg);
          for( int i=0; i<_aggs[gID].length; i++ )
            _aggs[gID][i].atomic_op(lg._dss,lg._ns,i, rg._dss[i], rg._ns[i]); // Need to atomically merge groups here
        }
    }
  }
}


/**
 * FeatureBuilder expects to flatten a table of predictors against a single column.
 *
 *
 */
class AggFeatureBuilder {
  String _primaryAgg;
  FrameMeta _fm;
  String[] _predictors;
  AggFeatureBuilder(Frame fr, String primaryAgg, String[] predictors) {
    _primaryAgg=primaryAgg;
    _predictors=predictors;
    _fm = new FrameMeta(fr,-1,fr._key.toString());
  }


  private void buildAggs() {
    ArrayList<ASTGroup.AGG[]> agg = new ArrayList<>();
    ArrayList<Integer[]> gbCols = new ArrayList<>();

    int primaryAgg = _fm._fr.find(_primaryAgg);
    int ncatfeats= _fm.numberOfCategoricalFeatures();
    for(int i=0;i<ncatfeats;++i) {

    }
  }
}
