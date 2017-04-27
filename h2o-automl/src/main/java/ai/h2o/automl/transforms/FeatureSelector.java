package ai.h2o.automl.transforms;

import ai.h2o.automl.FrameMetadata;
import ai.h2o.automl.UserFeedback;
import hex.Model;
import hex.ScoreKeeper;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import water.H2O;
import water.Job;
import water.Key;
import water.MRTask;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.AtomicUtils;

import java.util.HashMap;

import static ai.h2o.automl.utils.AutoMLUtils.makeWeights;

/**
 * Stepwise feature generation and pruning using various strategies.
 *
 * Two strategies are employed in this first cut:
 *      1. Using GBMs
 *      2. Using Mutual Information
 */
public abstract class FeatureSelector {

  final String[] _predictors;
  final String _response;
  final boolean _isClass;
  FrameMetadata _fm;

  private HashMap<String,Expr> _basicTransformsToTry;  // hold basic transforms that have not yet been vetted
  private HashMap<String,Expr> _basicTransformsToKeep; // these are transforms we want to keep
  private FeatureFactory.AggFeatureBuilder _aggFeatureBuilder; // iterate thru the aggregates and null out the uninteresting cases
  private UserFeedback _userFeedback;

  FeatureSelector(UserFeedback userFeedback, Frame f, String[] predictors, String response, boolean isClass) {
    _predictors=predictors;
    _response=response;
    _isClass=isClass;
    _userFeedback = userFeedback;
    _fm = new FrameMetadata(userFeedback, f,f.find(response),predictors,f._key.toString(),isClass);
  }

  public void debugRunAll() {
    genBasicFeatures();
    int idx=0;
    Vec[] vecs = new Vec[_basicTransformsToTry.size()];
    long start = System.currentTimeMillis();
    for(Expr v: _basicTransformsToTry.values()) vecs[idx++] = v.toWrappedVec().makeVec();
    System.out.println("Created transforms in: " + (System.currentTimeMillis()-start)/1000. + " seconds");
    validateVec(vecs, _basicTransformsToTry.keySet().toArray(new String[_basicTransformsToTry.size()]));
//    validateVec(_fm._fr.vecs(_predictors),_predictors);
    for(Vec v: vecs) v.remove();
  }

  public void debugComputeAggs() {

  }

  public void genBasicFeatures() {
    if( _fm.numberOfNumericFeatures() > 0 ) {
      _basicTransformsToTry = new HashMap<>();
      _basicTransformsToKeep= new HashMap<>();
      String op;
      for(int i=0;i<_fm._numFeats.length;++i) {
        String name = _fm._cols[_fm._numFeats[i]]._name + "_" + (op=_fm._cols[_fm._numFeats[i]].selectBasicTransform());
        if(op.equals("ignore")) continue;
        if( op.equals("time")) throw H2O.unimpl("does not generate time-based ops yet...");
        switch (op) {
          case "none":
            _basicTransformsToTry.put(name, Expr.unOp(op, _fm._cols[_fm._numFeats[i]]._v));
            break;
          case "recip":
            _basicTransformsToTry.put(name, Expr.binOp("/", 1, _fm._cols[_fm._numFeats[i]]._v));
            break;
          default:
            _basicTransformsToTry.put(name, Expr.unOp(op, _fm._cols[_fm._numFeats[i]]._v));
            break;
        }
      }
    }
    tryRatesAndReciprocals();
    tryCombos();
  }

  void tryRatesAndReciprocals() {
    int cnt=0;
    int maxCnt=5000;
    for(int i: _fm._dblCols) {
      for(int j: _fm._intNotBinaryCols) {
        if( cnt >= maxCnt ) return;
        String name = "("+_fm._cols[i]._name+")" + "_rate_" + "("+_fm._cols[j]._name+")";
        _basicTransformsToTry.put(name, Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v));
        cnt++;
        name = "("+_fm._cols[i]._name+")" + "_recip_" + "("+_fm._cols[j]._name+")";
        _basicTransformsToTry.put(name, Expr.binOp("/",1,Expr.binOp("/", _fm._cols[i]._v, _fm._cols[j]._v)));
      }
    }

    // now try to do all the pairwise additions of double columns divided by every int col
    for(int i=0;i<_fm._dblCols.length;++i)
      for(int j=i+1;j<_fm._dblCols.length;++j)
        for(int k: _fm._intNotBinaryCols) {
          if( cnt >= maxCnt ) return;
          String name = "("+_fm._cols[_fm._dblCols[i]]._name+")" + "_plus_" + "("+_fm._cols[_fm._dblCols[j]]._name+")" + "_rate_" + "("+_fm._cols[k]._v+")";
          _basicTransformsToTry.put(name, Expr.binOp("+",_fm._cols[_fm._dblCols[i]]._v,_fm._cols[_fm._dblCols[j]]._v).bop("/",_fm._cols[k]._v));
          cnt++;
          name = "("+_fm._cols[_fm._dblCols[i]]._name+")" + "_plus_" + "("+_fm._cols[_fm._dblCols[i]]._name+")" + "_recip" + "("+_fm._cols[k]._v+")";
          _basicTransformsToTry.put(name, Expr.binOp("/",1,Expr.binOp("+",_fm._cols[_fm._dblCols[i]]._v,_fm._cols[_fm._dblCols[j]]._v).bop("/",_fm._cols[k]._v)));
        }

    // now with all combinations of three double vecs
    for(int i=0;i<_fm._dblCols.length;++i)
      for(int j=i+1;j<_fm._dblCols.length;++j)
        for(int k=j+1;k<_fm._dblCols.length;++k)
          for(int l: _fm._intNotBinaryCols) {
            if( cnt >= maxCnt ) return;
            String name = "("+_fm._cols[_fm._dblCols[i]]._name+")" + "_plus_" + "("+_fm._cols[_fm._dblCols[j]]._name+ ")" + "_plus_"  + "("+_fm._cols[_fm._dblCols[k]]._name+")" + "_rate_" + "("+_fm._cols[l]._v+")";
            _basicTransformsToTry.put(name, Expr.binOp("+",_fm._cols[_fm._dblCols[i]]._v,_fm._cols[_fm._dblCols[j]]._v).bop("+",_fm._cols[_fm._dblCols[k]]._v).bop("/",_fm._cols[l]._v));
            cnt++;
            name = "("+_fm._cols[_fm._dblCols[i]]._name+")" + "_plus_" + "("+_fm._cols[_fm._dblCols[j]]._name+")" +"_plus_" + "("+_fm._cols[_fm._dblCols[k]]._name+")" + "_recip_" + "("+_fm._cols[l]._v+")";
            _basicTransformsToTry.put(name, Expr.binOp("/",1,Expr.binOp("+",_fm._cols[_fm._dblCols[i]]._v,_fm._cols[_fm._dblCols[j]]._v).bop("+",_fm._cols[_fm._dblCols[k]]._v).bop("/",_fm._cols[l]._v)));
          }

    // now all the integer (non binary) columns with each other:
    for(int i=0;i<_fm._intNotBinaryCols.length;++i)
      for(int j=i+1;j<_fm._intNotBinaryCols.length;++j) {
        if( cnt >= maxCnt ) return;
        String name = "("+_fm._cols[_fm._intNotBinaryCols[i]]._name + ")" + "_rate_" + "(" + _fm._cols[_fm._intNotBinaryCols[j]]._name + ")";
        _basicTransformsToTry.put(name, Expr.binOp("/", _fm._cols[_fm._intNotBinaryCols[i]]._v, _fm._cols[_fm._intNotBinaryCols[j]]._v));
        cnt++;
        name = "("+_fm._cols[_fm._intNotBinaryCols[i]]._name+")" + "_recip_" + "("+_fm._cols[_fm._intNotBinaryCols[j]]._name+")";
        _basicTransformsToTry.put(name, Expr.binOp("/",1,Expr.binOp("/", _fm._cols[_fm._intNotBinaryCols[i]]._v, _fm._cols[_fm._intNotBinaryCols[j]]._v)));
      }
  }

  void tryCombos() {
    int cnt=0;
    int maxCnt=5000;
    String[] transforms = _basicTransformsToTry.keySet().toArray(new String[_basicTransformsToTry.size()]);
    for(int i=0;i<transforms.length;++i)
      for(int j=i+1;j<transforms.length;++j) {
        if( cnt >= maxCnt ) return;
        String name = "("+transforms[i] +")" + "_plus_" + "(" + transforms[j] + ")";
        _basicTransformsToTry.put(name, _basicTransformsToTry.get(transforms[i]).clone().bop("+", _basicTransformsToTry.get(transforms[j]).clone()));
        cnt++;
      }
  }

  void tryCatInteractions() {
    _fm.numberOfCategoricalFeatures();
    Model.InteractionPair[] ips = Model.InteractionPair.generatePairwiseInteractionsFromList(_fm._catFeats);
    InteractionWrappedVec[] vecs = Model.makeInteractions(_fm._fr, ips, true,false,false);
  }


  public Vec[] tryNextAggs() {
    if( null==_aggFeatureBuilder ) throw new IllegalArgumentException("no aggregates yet.");
    return null;
  }


  public void buildAggs() {
    Vec[]    vecs  = new Vec[_predictors.length + _basicTransformsToKeep.size()];
    String[] names = new String[vecs.length];
    System.arraycopy(_fm._fr.vecs(_predictors),0,vecs,0,_predictors.length);
    System.arraycopy(_predictors,0,names,0,_predictors.length);
    int idx=_predictors.length;
    for(String name: _basicTransformsToKeep.keySet()) {
      vecs[idx] = _basicTransformsToKeep.get(name).toWrappedVec();
      names[idx++] = name;
    }
    Frame fullFrame = new Frame(names, vecs.clone());
    FrameMetadata fullFrameMetadata = new FrameMetadata(_userFeedback, fullFrame,fullFrame.find(_response),names, "synthesized_fullFrame",_isClass);
    _aggFeatureBuilder = FeatureFactory.AggFeatureBuilder.buildAggFeatures(fullFrameMetadata);
  }

  protected abstract void validateVec(Vec v[], String[] name);
  protected abstract void valdiateVecs(TransformWrappedVec[] vecs);
}

class GBMSelector extends FeatureSelector {

  GBMSelector(UserFeedback userFeedback, Frame fm, String[] predictors, String response, boolean isClass) {
    super(userFeedback, fm, predictors, response, isClass);
  }

  @Override protected void validateVec(Vec v[], String name[]) {
    throw H2O.unimpl();
  }

  @Override protected void valdiateVecs(TransformWrappedVec[] vecs) {
    throw H2O.unimpl();
  }

  // launch a model and monitor performance on some holdout set
  // first 5 trees, if the MSE/AUC/CLASSERR is worse than "base" values, then we cut it and dump the feature
  // on the other hand, if perf metrics are better than "base" values, then this task will
  // preserve those transformations.
  //
  // open question: at what point should the holdout set be generated?
  //                idea is that we may be able to sample out a subset of the entire data
  //                to improve the rate at which we can validate new features
  private static class LaunchAndMonitor extends H2O.H2OCountedCompleter<LaunchAndMonitor> {
    Frame _taskTrain;
    Frame _taskValid;
    String _response;
    String[] _ignored;
    Job _job;                   // the Job holding the GBMModel
    Key _result;                // the GBMModel
    ScoreKeeper _hist;          // historical scores on 5-tree 5-depth GBM
    boolean _continueBuilding;  // keep running the model build?
    // base values
    double _s; // perf << _s ? kill_task : save_task   NaN => _l!=NaN
    double _l; // perf >> _l ? kill_task : save_task   NaN => _s!=NaN



    LaunchAndMonitor(Frame frame, double s, double l, String response, String[] ignored) {
      assert Double.isNaN(s) || Double.isNaN(l) : "expected one of the base perf values to not be NaN!";
      _s=s;
      _l=l;
      _response=response;
      _ignored=ignored;
      Vec[] vecs = new Vec[frame.numCols()+1];
      String[] names = new String[frame.numCols()+1];
      System.arraycopy(frame.names(),0,names,0,frame.numCols());
      names[names.length-1] = "weight";
      for(int i=0;i<vecs.length;++i)
        vecs[i] = frame.vec(i).clone(); // clone vec headers now b4 launch
      Vec[] trainTestWeights = makeWeights(frame.anyVec(), 0.8,null);
      vecs[vecs.length-1] = trainTestWeights[0];
      _taskTrain = new Frame(names,vecs.clone());  // reclone for safety
      vecs = vecs.clone();
      vecs[vecs.length-1] = trainTestWeights[1];
      _taskValid = new Frame(names,vecs.clone()); // reclone for safety
    }

    @Override public void compute2() {
      // construct a new model
      GBM mb = makeModelBuilder(genParms());
      _result = mb.dest();
      mb._parms._train = _taskTrain._key;
      mb._parms._valid = _taskValid._key;
      mb._parms._ignored_columns = _ignored;
      mb._parms._response_column = _response;

      // start the job
      _job = mb.trainModel();
      int ntree = mb._parms._ntrees;
      while( hasMoreTreesToBuild(ntree) ) {  // while has more trees to build
        GBMModel m = ((GBMModel)_result.get());
        validationScoreKeeper(m._output._scored_valid,m._output._ntrees,m._output._training_time_ms);
      }
    }

    private boolean hasMoreTreesToBuild(int ntrees) { return _continueBuilding && ((GBMModel)_result.get())._output._ntrees <= ntrees; }

    private static void validationScoreKeeper(ScoreKeeper sk[], int nScores, long[] treeBuildTimeStamps) {
      if( nScores <1 ) return; // keep running
      if( nScores==1 ) {       // single tree built,
        //TODO
      }
    }

    private static double averageBuildTime(long[] ts) {
      double sum=0;
      for(int i=1;i<ts.length;++i)
        sum+=ts[i] - ts[i-1];
      return sum / (double)(ts.length-1);
    }

    protected GBMModel.GBMParameters genParms() {
      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._ntrees = 5;
      p._max_depth = 5;
      p._score_each_iteration=true;
      return p;
    }
    protected GBM makeModelBuilder(Model.Parameters p) { return new GBM((GBMModel.GBMParameters)p); }
  }
}

// compute a mutual information score for each transformation
class MISelector extends FeatureSelector {

  MISelector(UserFeedback userFeedback, Frame fm, String[] predictors, String response, boolean isClass) {
    super(userFeedback, fm, predictors, response, isClass);
  }

  @Override protected void validateVec(Vec v[], String name[]) {
    MIComputer mic = new MIComputer(v,_fm.response()._v,name);
    mic.compute2();
//    H2O.submitTask(mic).join();
  }

  @Override protected void valdiateVecs(TransformWrappedVec[] vecs) {
    throw H2O.unimpl();
  }


  /**
   * Compute the mutual information between a predictor and the response to understand the
   * "decrease in uncertainty about the response given the predictor".
   *
   * Continuous columns are discretized by computing a set of 10 quantiles and performing
   * the bucket lookup on-the-fly.
   */
  private static class MIComputer {
    final String _name[]; // name of the pred columns
    final Vec[] _v;  // the pred columns
    final Vec _r;  // the resp column
    final double _hr; // the shannon entropy for the response vector

    double _mi;

    MIComputer(Vec[] preds, Vec response, String name[]) {
      _v=preds;
      _r=response;
      _name=name;
      if( _r.isCategorical() ) _hr = entropy(_r.bins());
      else throw H2O.unimpl("No mutual information impl for numeric response.");
    }

    public void compute2() {
      double hx[],hy=_hr,hxy[];
      Vec[] vecs = new Vec[_v.length+1];
      System.arraycopy(_v,0,vecs,0,_v.length);
      vecs[vecs.length-1]=_r;
      double[][] lookups;
      double[] probs = new double[]{0.0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.9,1.0};
      lookups = makeLookup(_v,probs);
      long start = System.currentTimeMillis();
      MITask mit = new MITask(lookups,_r.length()-_r.naCnt(),_r.domain().length*lookups[0].length,_r.domain().length,vecs.length-1).doAll(vecs);
      hx=mit._hx;
      hxy=mit._hxy;
      for(int i=0;i<hx.length;++i) {
        double mi = hx[i] + hy - hxy[i];
        if( mi < 0.05 ) {
          _v[i].remove();
          _v[i]=null;
          _name[i]=null;
        } else {
          System.out.println("mi>=0.05: " + i + "; name="+_name[i] + "; mi=" + mi);
        }
      }
      System.out.println("MutualInfo computed in " + (System.currentTimeMillis()-start)/1000. + " seconds");
      System.out.println("done!");
    }

    private static double[][] makeLookup(Vec[] vecs, double[] probs) {
      long start = System.currentTimeMillis();

      double[][] lookups = new double[vecs.length][];
      for(int i=0;i<lookups.length;++i)
        lookups[i] = vecs[i].pctiles();
      System.out.println("quantiles made in: " + (System.currentTimeMillis() - start)/1000. + " seconds");

//
//      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
//      Frame fr = new Frame(Key.make(), null,vecs);
//      DKV.put(fr);
//      parms._train = fr._key;
//      parms._probs  = probs;
//      Job j = new Quantile(parms).trainModel();
//      QuantileModel q = (QuantileModel)j.get();
//      System.out.println("quantiles made in: " + (System.currentTimeMillis() - start)/1000. + " seconds");
//      DKV.remove(j._key);
//      DKV.remove(fr._key);
//      q.delete();
//      return q._output._quantiles;
      return lookups;
    }

//    private static void makeLookup(double probs[], Vec v) {
//      QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
//      Frame fr = new Frame(Key.make(),new String[]{"dummyFrameVec"},new Vec[]{v});
//      DKV.put(fr);
//      parms._train = fr._key;
//      parms._probs  = probs;
//      Job j = new Quantile(parms).trainModel();
//      QuantileModel q = (QuantileModel)j.get();
//      DKV.remove(j._key);
//      DKV.remove(fr._key);
//      System.arraycopy(q._output._quantiles[0],0,probs,0,probs.length);
//    }

    static double entropy(long[] bins) {
      double res=0;
      double sum=ArrayUtils.sum(bins);
      for (long bin : bins) res += (1. * bin / sum) * log2(1. * bin / sum);
      return -1*res;
    }
    static double log2(double numerator) { return (Math.log(numerator))/Math.log(2)+1e-10; }

    /**
     * Mutual Information:
     *    MI = H_X + H_Y - H_XY
     */
    private static class MITask extends MRTask<MITask> {
      private final double[][] _lookup;
      private final double _incr;  // the normalized increment (instead of +=1, += 1/total)  total is response.length - response.nacnt
      private final int _sz;       // x.domain().length * y.domain().length
      private final int _yDomainLength; // y.domain().length -> used for help in direct accessing into _normedXY
      private final int _nVecs;       // number of vecs we're trying to comptue MI for

      private double[] _hx;  // x entropy (computed if null!=_lookup)
      private double[] _hxy; // joint entropy: -sum(v*log2(v))
      private double[][] _normedX; // computed only when null!=_lookup
      private double[][] _normedXY; // atomically udpated, table(X,Y)

      /**
       *
       * @param lookup quantiles for continous columns
       * @param cnt  the length of 1 vector (both vectors are same length)
       */
      MITask(double[][] lookup, double cnt, int sz, int yDomainLength, int nVecs) {
        _lookup=lookup;
        _incr=1./cnt;
        _sz=sz;
        _yDomainLength=yDomainLength;
        _nVecs=nVecs;
      }
      @Override public void setupLocal() { _normedXY = new double[_nVecs][_sz]; _normedX=null!=_lookup?new double[_nVecs][_sz/_yDomainLength]:null; }
      @Override public void map(Chunk[] c) {
        Chunk resp = c[c.length-1];
        int nrow = resp._len;
        if( null==_lookup ) { // ok to at8 everything
          for(int col=0;col<c.length-1;++col) {
            for(int row=0;row<nrow;++row) {
              if( c[col].isNA(row) || resp.isNA(row) ) continue; // NA skip
              AtomicUtils.DoubleArray.add(_normedXY[col], idx(c[col].at8(row), resp.at8(row)), _incr);
            }
          }
        }
        else
          for(int col=0;col<c.length-1;col++) {
            for(int row=0;row<nrow;row++) {
              if( c[col].isNA(row) || resp.isNA(row) ) continue; // NA skip
              AtomicUtils.DoubleArray.add(_normedXY[col], idx(doLookup(c[col].atd(row),col), resp.at8(row)), _incr);
            }
          }
      }
      @Override public void reduce(MITask t) {
        if( t._normedXY!=_normedXY ) ArrayUtils.add(_normedXY, t._normedXY);
      }
      @Override public void postGlobal(){
        _hxy=new double[_normedXY.length];
        for(int c=0;c<_normedXY.length;++c) {
          for (double d : _normedXY[c]) _hxy[c] += d == 0 ? 0 : d * log2(d);
          _hxy[c]*=-1;
        }

        if( null!=_normedX ) {
          _hx=new double[_normedX.length];
          for(int c=0;c<_normedX.length;++c) {
            for (double d : _normedX[c]) _hx[c] += d == 0 ? 0 : d * log2(d);
            _hx[c] *= -1;
          }
        }
      }

      private int idx(long l, long r) { return (int)(l*_yDomainLength + r); }
      private int doLookup(double d,int c) {
        int x=0;
        while( x<_lookup[c].length && d>_lookup[c][x] ) x++;
        x = Math.min(_lookup[c].length-1,x);
        AtomicUtils.DoubleArray.add(_normedX[c],x,_incr);
        return x;
      }
    }
  }
}