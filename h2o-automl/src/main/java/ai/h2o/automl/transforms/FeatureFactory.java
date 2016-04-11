package ai.h2o.automl.transforms;

import ai.h2o.automl.FrameMeta;
import hex.Model;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;

import java.util.HashMap;

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
  private final String _response;     // target column
  private final String[] _predictors; // names of predictor columns

  private HashMap<String,Expr> _basicTransforms;

  /**
   * Create a new FeatureFactory for this Frame
   * @param f Frame upon which to generate new features
   */
  public FeatureFactory(Frame f, String[] predictors, String response) {
    _fm = new FrameMeta(f,f.find(response),f._key.toString());
    _predictors=predictors;
    _response=response;
  }


  /**
   * Launch the generation and pruning process.
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
      cnt += tryRates(nBasicFeats-cnt);
      cnt += tryCombos(nBasicFeats-cnt);
    }
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
  private int tryRates(int maxFeats) {
    int cnt=0;
    for(int i: _fm._dblCols) {
      for(int j: _fm._intNotBinaryCols) {
        String name = _fm._cols[i]._name + "_rate_" + _fm._cols[j]._name;
        _basicTransforms.put(name, Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v));
        cnt+=1;
        if( cnt >= maxFeats ) return cnt;
      }
    }

    // now try to do all the pairwise additions of double columns divided by every int col
    for(int i=0;i<_fm._dblCols.length;++i)
      for(int j=i+1;j<_fm._dblCols.length;++j)
        for(int k: _fm._intNotBinaryCols) {
          if( cnt >= maxFeats ) return cnt;
          String name = _fm._cols[i]._name + "_plus_" + _fm._cols[j]._name + "_rate_" + _fm._cols[k]._v;
          _basicTransforms.put(name, Expr.binOp("+",_fm._cols[i]._v,_fm._cols[j]._v).bop("/",_fm._cols[k]._v));
          cnt++;
        }

    // now with all combinations of three double vecs
    for(int i=0;i<_fm._dblCols.length;++i)
      for(int j=i+1;j<_fm._dblCols.length;++j)
        for(int k=j+1;k<_fm._dblCols.length;++k)
          for(int l: _fm._intNotBinaryCols) {
            if( cnt >= maxFeats ) return cnt;
            String name = _fm._cols[i]._name + "_plus_" + _fm._cols[j]._name + _fm._cols[k]._name + "_rate_" + _fm._cols[l]._v;
            _basicTransforms.put(name, Expr.binOp("+",_fm._cols[i]._v,_fm._cols[j]._v).bop("+",_fm._cols[k]._v).bop("/",_fm._cols[l]._v));
            cnt++;
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


  // launch a model and monitor performance on some holdout set
  // for sake of getting something done, the model is GBM and we monitor first
  // 5 trees, if the MSE/AUC/CLASSERR is worse than "base" values, then we cut and run...
  // on the other hand, if perf metrics are better than "base" values, then this task will
  // preserve those transformations.
  //
  // open question: at what point should the holdout set be generated?
  private static class LaunchAndMonitor extends H2O.H2OCountedCompleter<LaunchAndMonitor> {

    Frame _taskTrain;
    Frame _taskValid;
    String _response;
    String[] _ignored;


    // base values
    double _s; // perf << _s ? kill_task : save_task   NaN => _l!=NaN
    double _l; // perf >> _l ? kill_task : save_task   NaN => _s!=NaN

    LaunchAndMonitor(Frame frame, double s, double l, String response, String[] ignored, Vec weight) {
      assert Double.isNaN(s) || Double.isNaN(l) : "expected one of the base perf values to not be NaN!";
      Vec[] vecs = new Vec[frame.numCols()+1];
      String[] names = new String[frame.numCols()+1];
      System.arraycopy(frame.names(),0,names,0,frame.numCols());
      names[names.length-1] = "weight";
      for(int i=0;i<vecs.length;++i)
        vecs[i] = frame.vec(i).clone(); // clone vec headers now b4 launch
      vecs[vecs.length-1] = weight;
      _taskTrain = new Frame(names,vecs.clone());
//      vecs[vecs.length-1]
//      _taskValid = new Frame(names,)
      _s=s;
      _l=l;
      _response=response;
      _ignored=ignored;
    }

    @Override public void compute2() {
//      ModelBuilder mb = makeModelBuilder(genParms());
//      mb._parms._train=fs==null?fr._key:fs[0]._key;
//      mb._parms._valid=fs==null?test._key:fs[1]._key;
//      mb._parms._ignored_columns = ignored;
//      mb._parms._response_column = y;
//      mb.trainModel();
    }

    protected GBMModel.GBMParameters genParms() {
      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._ntrees = 5;
      p._max_depth = 5;
      return p;
    }
    protected GBM makeModelBuilder(Model.Parameters p) { return new GBM((GBMModel.GBMParameters)p); }
  }
}
