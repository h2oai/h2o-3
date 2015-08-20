package hex.glm;

import hex.BasicStats;
import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.glm.GLMModel.*;
import hex.optimization.ADMM.L1Solver;
import hex.optimization.L_BFGS;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMTask.*;
import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import hex.gram.Gram.NonSPDMatrixException;
import hex.optimization.ADMM;
import hex.optimization.ADMM.ProximalSolver;
import hex.optimization.L_BFGS.*;
import hex.schemas.GLMV3;
import hex.schemas.ModelBuilderSchema;
import jsr166y.CountedCompleter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.H2O.H2OCallback;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.H2O.H2OCountedCompleter;
import water.parser.ValueString;
import water.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 */
public class GLM extends ModelBuilder<GLMModel,GLMParameters,GLMOutput> {
  static final double LINE_SEARCH_STEP = .5;
  static final int NUM_LINE_SEARCH_STEPS = 16;

  public boolean isSupervised(){return true;}
  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{
            ModelCategory.Regression,
            ModelCategory.Binomial,
    };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; };

  @Override protected void checkMemoryFootPrint() {/* see below */ }
  protected void checkMemoryFootPrint(DataInfo dinfo) {
    if (_parms._solver == Solver.IRLSM && !_parms._lambda_search) {
      HeartBeat hb = H2O.SELF._heartbeat;
      double p = dinfo.fullN() - dinfo.largestCat();
      long mem_usage = (long)(hb._cpus_allowed * (p*p + dinfo.largestCat()) * 8/*doubles*/ * (1+.5*Math.log((double)_train.lastVec().nChunks())/Math.log(2.))); //one gram per core
      long max_mem = hb.get_max_mem();
      if (mem_usage > max_mem) {
        String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
                + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
                + ") - try reducing the number of columns and/or the number of categorical factors (or switch to the L-BFGS solver).";
        error("_train", msg);
        cancel(msg);
      }
    }
  }

  public GLM(Key dest, String desc, GLMModel.GLMParameters parms) { super(dest, desc, parms); init(false); }
  public GLM(GLMModel.GLMParameters parms) { super("GLM", parms); init(false); }

  static class TooManyPredictorsException extends RuntimeException {}

  private BetaConstraint _bc = new BetaConstraint();
  DataInfo _dinfo;
  private Vec _rowFilter;
  transient GLMTaskInfo [] _tInfos;

  public double likelihood(){
    return _tInfos[0]._ginfo._likelihood;
  }
  private int _lambdaId;
  private transient DataInfo _validDinfo;
  private transient ArrayList<Integer> _scoring_iters = new ArrayList<>();
  // time per iteration in ms

  private static class ScoringHistory {
    private ArrayList<Integer> _scoringIters = new ArrayList<>();
    private ArrayList<Long>    _scoringTimes = new ArrayList<>();
    private ArrayList<Double>  _likelihoods = new ArrayList<>();
    private ArrayList<Double>  _objectives = new ArrayList<>();
    private ArrayList<Double>  _lambdas = new ArrayList<>();
    private ArrayList<Integer> _lambdaIters = new ArrayList<>();
    private ArrayList<Integer> _lambdaPredictors = new ArrayList<>();
    private ArrayList<Double>  _lambdaDevTrain = new ArrayList<>();
    private ArrayList<Double>  _lambdaDevTest = new ArrayList<>();

    public synchronized void addIterationScore(int iter, double likelihood, double obj){
      if(_scoringIters.size() > 0 && _scoringIters.get(_scoringIters.size()-1) == iter)
        return; // do not record twice, happens for the last iteration, need to record scoring history in checkKKTs because of gaussian fam.
      _scoringIters.add(iter);
      _scoringTimes.add(System.currentTimeMillis());
      _likelihoods.add(likelihood);
      _objectives.add(obj);
    }

    public synchronized void addLambdaScore(int iter, double lambda, int preds, double devExpTrain, double devExpTest) {
      _lambdaIters.add(iter);
      _lambdas.add(lambda);
      _lambdaPredictors.add(preds);
      _lambdaDevTrain.add(devExpTrain);
      if(!Double.isNaN(devExpTest))
        _lambdaDevTest.add(devExpTest);
    }

    public synchronized TwoDimTable to2dTable() {
      String [] cnames = new String[]{"timestamp", "duration","iteration", "log_likelihood", "objective"};
      String [] ctypes = new String[]{"string","string","int", "double", "double"};
      String []cformats = new String[]{"%s","%s","%d", "%.5f", "%.5f"};
      if(_lambdaIters.size() > 1) { // lambda search info
        cnames =   ArrayUtils.append(cnames, new String  [] {"lambda","Number of Predictors","Explained Deviance (train)", "Explained Deviance (test)"});
        ctypes =   ArrayUtils.append(ctypes,  new String [] {"double" , "int",                       "double",          "double"});
        cformats = ArrayUtils.append(cformats, new String[] {"%.3f",    "%d",                        "%.3f",            "%.3f"});
      }
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_scoringIters.size()], cnames , ctypes, cformats , "");
      int j = 0;
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      for (int i = 0; i < _scoringIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, fmt.print(_scoringTimes.get(i)));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
        res.set(i, col++, _scoringIters.get(i));
        res.set(i, col++, _likelihoods.get(i));
        res.set(i, col++, _objectives.get(i));
        if(_lambdaIters.size() > 1 && j < _lambdaIters.size() && (_scoringIters.get(i).equals(_lambdaIters.get(j)))) {
          res.set(i, col++, _lambdas.get(j));
          res.set(i, col++, _lambdaPredictors.get(j));
          res.set(i, col++, _lambdaDevTrain.get(j));
          if(j < _lambdaDevTest.size())
            res.set(i, col++, _lambdaDevTest.get(j));
          j++;
        }
      }
      return res;
    }
  }
  private transient ScoringHistory _sc = new ScoringHistory();

  long _t0 = System.currentTimeMillis();


  private transient double _iceptAdjust = 0;

  private transient GLMModel _model;

  @Override public void init(boolean expensive) {
    _t0 = System.currentTimeMillis();
    super.init(expensive);

    hide("_balance_classes", "Not applicable since class balancing is not required for GLM.");
    hide("_max_after_balance_size", "Not applicable since class balancing is not required for GLM.");
    hide("_class_sampling_factors", "Not applicable since class balancing is not required for GLM.");
    _parms.validate(this);
    if (expensive) {
      System.out.println(BasicStats.computeBasicStats(_train.lastVec()));
      System.out.println("H2O got mean = " + _train.lastVec().mean() + ", min = " + _train.lastVec().min() + ",  _max = " + _train.lastVec().max());
      // bail early if we have basic errors like a missing training frame
      if (error_count() > 0) return;
      if(_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0)
        _parms._use_all_factor_levels= true;
      if(_parms._max_active_predictors == -1)
        _parms._max_active_predictors = _parms._solver == Solver.IRLSM ?6000:100000000;
      if (_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
      _dinfo = new DataInfo(Key.make(), _train, _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, hasWeights(), hasOffset());
      DKV.put(_dinfo._key, _dinfo);
      if(_valid != null) {
        _validDinfo = _dinfo.validDinfo(_valid);
        DKV.put(_validDinfo._key, _validDinfo);
      }
      checkMemoryFootPrint(_dinfo);
      // handle BetaConstraints if I got them
      double[] betaStart = null;
      double[] betaGiven = null;
      double[] betaLB = null;
      double[] betaUB = null;
      double[] rho = null;
      if (_parms._beta_constraints != null) {
        Frame beta_constraints = _parms._beta_constraints.get();
        Vec v = beta_constraints.vec("names");
        String[] dom;
        int[] map;
        if (v.isString()) {
          dom = new String[(int) v.length()];
          map = new int[dom.length];
          ValueString vs = new ValueString();
          for (int i = 0; i < dom.length; ++i) {
            dom[i] = v.atStr(vs, i).toString();
            map[i] = i;
          }
          // check for dups
          String [] sortedDom = dom.clone();
          Arrays.sort(sortedDom);
          for(int i = 1; i < sortedDom.length; ++i)
            if(sortedDom[i-1].equals(sortedDom[i]))
              throw new IllegalArgumentException("Illegal beta constraints file, got duplicate constraint for predictor '" + sortedDom[i-1] +"'!");
        } else if (v.isEnum()) {
          dom = v.domain();
          map = FrameUtils.asInts(v);
          // check for dups
          int [] sortedMap = MemoryManager.arrayCopyOf(map,map.length);
          Arrays.sort(sortedMap);
          for(int i = 1; i < sortedMap.length; ++i)
            if(sortedMap[i-1] == sortedMap[i])
              throw new IllegalArgumentException("Illegal beta constraints file, got duplicate constraint for predictor '" + dom[sortedMap[i-1]] +"'!");
        } else
          throw new IllegalArgumentException("Illegal beta constraints file, names column expected to contain column names (strings)");
        // for now only enums allowed here
        String[] names = ArrayUtils.append(_dinfo.coefNames(), "Intercept");
        if (!Arrays.deepEquals(dom, names)) { // need mapping
          HashMap<String, Integer> m = new HashMap<String, Integer>();
          for (int i = 0; i < names.length; ++i)
            m.put(names[i], i);
          int[] newMap = MemoryManager.malloc4(dom.length);
          for (int i = 0; i < map.length; ++i) {
            Integer I = m.get(dom[map[i]]);
            if (I == null)
              throw new IllegalArgumentException("Unrecognized coefficient name in beta-constraint file, unknown name '" + dom[map[i]] + "'");
            newMap[i] = I;
          }
          map = newMap;
        }
        final int numoff = _dinfo.numStart();
        String [] valid_col_names = new String[]{"names","beta_given","beta_start","lower_bounds","upper_bounds","rho"};
        Arrays.sort(valid_col_names);
        for(String s:beta_constraints.names())
          if(Arrays.binarySearch(valid_col_names,s) < 0)
            error("beta_constraints","Unknown column name '" + s + "'");
        if ((v = beta_constraints.vec("beta_start")) != null) {
          betaStart = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          for (int i = 0; i < (int) v.length(); ++i)
            betaStart[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("beta_given")) != null) {
          betaGiven = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          for (int i = 0; i < (int) v.length(); ++i)
            betaGiven[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("upper_bounds")) != null) {
          betaUB = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          Arrays.fill(betaUB, Double.POSITIVE_INFINITY);
          for (int i = 0; i < (int) v.length(); ++i)
            betaUB[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("lower_bounds")) != null) {
          betaLB = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          Arrays.fill(betaLB, Double.NEGATIVE_INFINITY);
          for (int i = 0; i < (int) v.length(); ++i)
            betaLB[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("rho")) != null) {
          rho = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          for (int i = 0; i < (int) v.length(); ++i)
            rho[map == null ? i : map[i]] = v.at(i);
        }
        if (_dinfo._normMul != null) {
          double normG = 0, normS = 0;
          for (int i = numoff; i < _dinfo.fullN(); ++i) {
            double s = _dinfo._normSub[i - numoff];
            double d = 1.0 / _dinfo._normMul[i - numoff];
            if (betaUB != null && !Double.isInfinite(betaUB[i]))
              betaUB[i] *= d;
            if (betaLB != null && !Double.isInfinite(betaUB[i]))
              betaLB[i] *= d;
            if (betaGiven != null) {
              normG += betaGiven[i] * s;
              betaGiven[i] *= d;
            }
            if (betaStart != null) {
              normS += betaStart[i] * s;
              betaStart[i] *= d;
            }
          }
          if (_dinfo._intercept) {
            int n = _dinfo.fullN();
            if (betaGiven != null)
              betaGiven[n] -= normG;
            if (betaStart != null)
              betaStart[n] -= normS;
          }
        }
        if(betaStart == null && betaGiven != null)
          betaStart = betaGiven.clone();
        if(betaStart != null) {
          if (betaLB != null || betaUB != null) {
            for (int i = 0; i < betaStart.length; ++i) {
              if (betaLB != null && betaLB[i] > betaStart[i])
                betaStart[i] = betaLB[i];
              if (betaUB != null && betaUB[i] < betaStart[i])
                betaStart[i] = betaUB[i];
            }
          }
        }
        _bc.setBetaStart(betaStart).setLowerBounds(betaLB).setUpperBounds(betaUB).setProximalPenalty(betaGiven, rho);
      }
      if(_parms._non_negative) {
        if(_bc._betaLB != null) {
          for (int i = 0; i < _bc._betaLB.length - 1; ++i)
            _bc._betaLB[i] = Math.max(0, _bc._betaLB[i]);
        } else {
          _bc._betaLB = MemoryManager.malloc8d(_dinfo.fullN() + 1);
          _bc._betaLB[_dinfo.fullN()] = Double.NEGATIVE_INFINITY;
          _bc._betaUB = MemoryManager.malloc8d(_dinfo.fullN() + 1);
          Arrays.fill(_bc._betaUB,Double.POSITIVE_INFINITY);
        }
      }
      _tInfos = new GLMTaskInfo[_parms._n_folds + 1];
      InitTsk itsk = new InitTsk(0, _parms._intercept, null);
      H2O.submitTask(itsk).join();

      assert itsk._nobs == 0 || itsk._gtNull != null;
      assert itsk._nobs == 0 || itsk._nobs == itsk._gtNull._nobs:"unexpected nobs, " + itsk._nobs + " != " + itsk._gtNull._nobs;// +", filterVec = " + (itsk._gtNull._rowFilter != null) + ", nrows = " + itsk._gtNull._rowFilter.length() + ", mean = " + itsk._gtNull._rowFilter.mean()
      assert _rowFilter.nChunks() == _dinfo._adaptedFrame.anyVec().nChunks();
      assert Math.abs((_dinfo._adaptedFrame.numRows() - _rowFilter.mean() * _rowFilter.length()) - itsk._nobs) < 1e-8:"unexpected nobs, expected " + itsk._nobs + ", but got " + (_dinfo._adaptedFrame.numRows() - _rowFilter.mean() * _rowFilter.length());
      assert _rowFilter != null;
      if (itsk._nobs == 0) { // can happen if all rows have missing value and we're filtering missing out
        error("training_frame", "Got no data to run on after filtering out the rows with missing values.");
        return;
      }
      if (itsk._yMin == itsk._yMax) {
        error("response", "Can not run glm on dataset with constant response. Response == " + itsk._yMin + " for all rows in the dataset after filtering out rows with NAs, got " + itsk._nobs + " rows out of " + _dinfo._adaptedFrame.numRows() + " rows total.");
        return;
      } if (itsk._nobs < (_dinfo._adaptedFrame.numRows() >> 1)) { // running less than half of rows?
        warn("_training_frame", "Dataset has less than 1/2 of the data after filtering out rows with NAs");
      }
      if(_parms._prior > 0)
        _iceptAdjust = -Math.log(itsk._ymu * (1-_parms._prior)/(_parms._prior * (1-itsk._ymu)));
      // GLMTaskInfo(Key dstKey, int foldId, long nobs, double ymu, double lmax, double[] beta, GradientInfo ginfo, double objVal){
      GLMGradientTask gtBetastart = itsk._gtBetaStart != null?itsk._gtBetaStart:itsk._gtNull;
      _bc.adjustGradient(itsk._gtNull._beta,itsk._gtNull._gradient);
      if(_parms._alpha == null)
        _parms._alpha = new double[]{_parms._solver == Solver.IRLSM || _parms._solver == Solver.COORDINATE_DESCENT_NAIVE ?.5:0};
      double lmax =  lmax(itsk._gtNull);
      double objval = gtBetastart._likelihood/gtBetastart._nobs;
      double l2pen = .5 * lmax * (1 - _parms._alpha[0]) * ArrayUtils.l2norm2(gtBetastart._beta, _dinfo._intercept);
      l2pen += _bc.proxPen(gtBetastart._beta);
      objval += l2pen;
      double objStart = objVal(gtBetastart._likelihood,gtBetastart._beta, lmax, gtBetastart._nobs,_parms._intercept);
      _tInfos[0] = new GLMTaskInfo(_dest, 0, itsk._nobs, itsk._wsum, _parms._prior > 0?_parms._prior:itsk._ymu,lmax,_bc._betaStart, _dinfo.fullN() + (_dinfo._intercept?1:0), new GLMGradientInfo(gtBetastart._likelihood,objval, gtBetastart._gradient),objStart);
      _tInfos[0]._nullGradNorm = ArrayUtils.linfnorm(itsk._gtNull._gradient, false);
      _tInfos[0]._nullDevTrain = itsk._gtNull._val.nullDeviance();
      _sc.addIterationScore(0, gtBetastart._likelihood, objStart);
      if (_parms._lambda != null) { // check the lambdas
        ArrayUtils.mult(_parms._lambda, -1);
        Arrays.sort(_parms._lambda);
        ArrayUtils.mult(_parms._lambda, -1);
        int i = 0;
        while (i < _parms._lambda.length && _parms._lambda[i] > _tInfos[0]._lambdaMax) ++i;
        if (i == _parms._lambda.length)
          error("_lambda", "All passed lambda values are > lambda_max = " + _tInfos[0]._lambdaMax + ", nothing to compute.");
        if (i > 0) {
          _parms._lambda = Arrays.copyOfRange(_parms._lambda, i, _parms._lambda.length);
          warn("_lambda", "removed " + i + " lambda values which were greater than lambda_max = " + _tInfos[0]._lambdaMax);
        }
      } else { // fill in the default lambda(s)
        if (_parms._lambda_search) {
          if (_parms._nlambdas == 1)
             error("_nlambdas", "Number of lambdas must be > 1 when running with lambda_search!");
          if (_parms._lambda_min_ratio == -1)
            _parms._lambda_min_ratio = _tInfos[0]._nobs > 25 * _dinfo.fullN() ? 1e-4 : 1e-2;
          final double d = Math.pow(_parms._lambda_min_ratio, 1.0 / (_parms._nlambdas - 1));
          _parms._lambda = MemoryManager.malloc8d(_parms._nlambdas);
          _parms._lambda[0] = _tInfos[0]._lambdaMax;
          for (int i = 1; i < _parms._lambda.length; ++i)
            _parms._lambda[i] = _parms._lambda[i - 1] * d;
          _lambdaId = 1; // don't bother with lmax model (which we know already)
        } else
          _parms._lambda = new double[]{_tInfos[0]._lambdaMax * (_dinfo.fullN() < (_tInfos[0]._nobs >> 4) ? 1e-3 : 1e-1)};
      }
      _model = new GLMModel(_dest, _parms, GLM.this, _tInfos[0]._ymu, _dinfo._adaptedFrame.lastVec().sigma(),_tInfos[0]._lambdaMax, _tInfos[0]._nobs, hasWeights(), hasOffset());
      String [] warns = _model.adaptTestForTrain(_valid, true, true);
      for(String s:warns) warn("_validation_frame",s);
      final Submodel nullSm = new Submodel(_parms._lambda[0], _bc._betaStart, 0, itsk._gtNull._val.explainedDev(),itsk._gtNullTest != null?itsk._gtNullTest._val.residualDeviance():Double.NaN);
      _model.setSubmodel(nullSm);
      _model._output._training_metrics = itsk._gtNull._val.makeModelMetrics(_model,_parms.train(),_dinfo._adaptedFrame.lastVec().sigma());
      if(_valid != null)
        _model._output._validation_metrics = itsk._gtNullTest._val.makeModelMetrics(_model,_parms.valid(),_validDinfo._adaptedFrame.lastVec().sigma());
      _model.delete_and_lock(GLM.this._key);
//      if(_parms._solver == Solver.COORDINATE_DESCENT) { // make needed vecs
//        double eta = _parms.link(_tInfos[0]._ymu);
//        _tInfos[0]._eVec = _dinfo._adaptedFrame.anyVec().makeCon(eta);
//        _tInfos[0]._wVec = _dinfo._adaptedFrame.anyVec().makeCon(1);
//        _tInfos[0]._zVec = _dinfo._adaptedFrame.lastVec().makeCopy(null);
//        _tInfos[0]._iVec = _dinfo._adaptedFrame.anyVec().makeCon(1);
//      }
      if(_parms._max_iterations == -1) {
        if(_parms._solver == Solver.IRLSM) {

          _parms._max_iterations = _parms._lambda_search ? 10 * _parms._nlambdas : 50;
        } else {
          _parms._max_iterations = Math.max(20,_dinfo.fullN() >> 2);
          if(_parms._lambda_search)
            _parms._max_iterations = _parms._nlambdas * 100;
        }
      }
      _tInfos[0]._workPerIteration = _parms._lambda_search?0:(int)(WORK_TOTAL /_parms._max_iterations);
      _tInfos[0]._workPerLambda = (int)(_parms._lambda_search?(WORK_TOTAL/_parms._nlambdas):0);
    }
  }



  private class InitTsk extends H2OCountedCompleter {
    final int _foldId;
    final boolean _intercept;
    public InitTsk(int foldId, boolean intercept, H2OCountedCompleter cmp) { super(cmp); _foldId = foldId; _intercept = intercept; }
    long _nobs;
    double _ymu;
    double _wsum;
    double _ymuLink;
    double _yMin;
    double _yMax;
    GLMGradientTask _gtNull;
    GLMGradientTask _gtNullTest;
    GLMGradientTask _gtBetaStart;

    private transient double _likelihood = Double.POSITIVE_INFINITY;
    private transient int _iter;
    private class NullModelIteration extends H2OCallback<GLMIterationTask> {
      final DataInfo _nullDinfo;

      NullModelIteration(DataInfo dinfo) {
        super(InitTsk.this);
        _nullDinfo = dinfo;
      }
      @Override
      public void callback(GLMIterationTask glmIterationTask) {
        if(glmIterationTask._likelihood > _likelihood){ // line search
          if(++_iter  < 50) {
            InitTsk.this.addToPendingCount(1);
            new GLMTask.GLMIterationTask(GLM.this._key, _nullDinfo, 0, _parms, false, new double[]{.5 * (_ymu + glmIterationTask._beta[0])}, 0, _rowFilter, new NullModelIteration(_nullDinfo)).asyncExec(_nullDinfo._adaptedFrame);
          } else {
            _ymuLink = _ymu;
            _ymu = _parms.linkInv(_ymuLink);
            computeGradients();
          }
          return;
        }
        _likelihood = glmIterationTask._likelihood;
        _ymu = glmIterationTask._beta[0];
        double ymu = glmIterationTask._xy[0]/glmIterationTask._gram.get(0,0);
        if(++_iter < 50 && Math.abs(ymu - glmIterationTask._beta[0]) > _parms._beta_epsilon) {
          InitTsk.this.addToPendingCount(1);
          new GLMTask.GLMIterationTask(GLM.this._key,_nullDinfo,0,_parms,false,new double[]{ymu},0,_rowFilter, new NullModelIteration(_nullDinfo)).asyncExec(_nullDinfo._adaptedFrame);
        } else {
          System.out.println("computed null intercept in " + _iter + " iterations, intercept = " + _ymu);
          _ymuLink = _ymu;
          _ymu = _parms.linkInv(_ymuLink);
          computeGradients();
        }
      }
    }

    @Override
    protected void compute2() {
      // get filtered dataset's mean and number of observations
      new YMUTask(_dinfo, _dinfo._adaptedFrame.anyVec().makeZero(), new H2OCallback<YMUTask>(this) {
        @Override
        public void callback(final YMUTask ymut) {
          _rowFilter = ymut._fVec;
          _ymu = _parms._intercept ? ymut._ymu : 0;
          _wsum = ymut._wsum;
          _ymuLink = _parms._intercept ? _parms.link(_ymu):0;
          _yMin = ymut._yMin;
          _yMax = ymut._yMax;
          _nobs = ymut._nobs;
          if(_dinfo._offset && _parms._intercept) {
            InitTsk.this.addToPendingCount(1);
            DataInfo dinfo = _dinfo.filterExpandedColumns(new int[]{});
            new GLMIterationTask(GLM.this._key,dinfo,0,_parms,false,new double[]{_parms.link(_response.mean()) - _offset.mean()},0,_rowFilter, new NullModelIteration(dinfo)).asyncExec(dinfo._adaptedFrame);
          } else
            computeGradients();
        }
      }).asyncExec(_dinfo._adaptedFrame);
    }

    private void computeGradients(){
      if(_nobs > 0) {
        InitTsk.this.addToPendingCount(1);
        final double[] beta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        if (_intercept)
          beta[beta.length - 1] = _ymuLink;
        if (_bc._betaStart == null)
          _bc.setBetaStart(beta);
        // compute the lambda_max
        _gtNull = new GLMGradientTask(_dinfo, _parms, 0, beta, 1.0 / _nobs, _rowFilter, InitTsk.this).setValidate(_ymu,true).asyncExec(_dinfo._adaptedFrame);
        if(_validDinfo != null) {
          InitTsk.this.addToPendingCount(1);
          _gtNullTest = new GLMGradientTask(_validDinfo, _parms, 0, beta, 1.0, null, InitTsk.this).setValidate(_ymu,true).asyncExec(_validDinfo._adaptedFrame);
        }
        if (beta != _bc._betaStart) {
          InitTsk.this.addToPendingCount(1);
          _gtBetaStart = new GLMGradientTask(_dinfo, _parms, 0, _bc._betaStart, 1.0 / _nobs, _rowFilter, InitTsk.this).setValidate(_ymu,true).asyncExec(_dinfo._adaptedFrame);
        }
      }
    }
    @Override public void onCompletion(CountedCompleter cc){
      if(!_parms._intercept) { // null the intercept gradients
        _gtNull._gradient[_gtNull._gradient.length-1] = 0;
        if(_gtBetaStart != null)
          _gtBetaStart._gradient[_gtBetaStart._gradient.length-1] = 0;
      }
    }
  }
  @Override
  public ModelBuilderSchema schema() {
    return new GLMV3();
  }


  private static final long WORK_TOTAL = 1000000;
  @Override
  public Job<GLMModel> trainModel() {
    start(new GLMDriver(null), WORK_TOTAL);
    return this;
  }

  static double GLM_GRAD_EPS = 1e-4; // done (converged) if subgrad < this value.

  public static class BetaConstraint extends Iced {
    double [] _betaStart;
    double [] _betaGiven;
    double [] _rho;
    double [] _betaLB;
    double [] _betaUB;
    public BetaConstraint setLowerBounds(double [] lb) {_betaLB = lb; return this;}
    public BetaConstraint setUpperBounds(double [] ub) {_betaUB = ub; return this;}
    public BetaConstraint setBetaStart  (double [] bs) {_betaStart = bs; return this;}
    public BetaConstraint setProximalPenalty  (double [] bGiven, double [] rho) {
      _betaGiven = bGiven;
      _rho = rho;
      return this;
    }

    public String toString(){
      double [][] ary = new double[_betaGiven.length][3];

      for(int i = 0; i < _betaGiven.length; ++i) {
        ary[i][0] = _betaGiven[i];
        ary[i][1] = _betaLB[i];
        ary[i][2] = _betaUB[i];
      }
      return ArrayUtils.pprint(ary);
    }
    public boolean hasBounds(){
      if(_betaLB != null)
        for(double d:_betaLB)
          if(!Double.isInfinite(d)) return true;
      if(_betaUB != null)
        for(double d:_betaUB)
          if(!Double.isInfinite(d)) return true;
      return false;
    }

    public void adjustGradient(double [] beta, double [] grad) {
      if(_betaGiven != null && _rho != null) {
        for(int i = 0; i < _betaGiven.length; ++i) {
          double diff = beta[i] - _betaGiven[i];
          grad[i]  += _rho[i] * diff;
        }
      }
    }
    double proxPen(double [] beta) {
      double res = 0;
      if(_betaGiven != null && _rho != null) {
        for(int i = 0; i < _betaGiven.length; ++i) {
          double diff = beta[i] - _betaGiven[i];
          res += _rho[i] * diff * diff;
        }
        res *= .5;
      }
      return res;
    }
  }

  /**
   * Encapsulates state of the computation.
   */
  public static final class GLMTaskInfo extends Iced {
    final int       _foldId;
    final long      _nobs;       // number of observations in our dataset
    final double    _wsum;
    final double    _ymu;        // actual mean of the response
    final double    _lambdaMax;  // lambda max of the current dataset
    double []       _beta;       // full - solution at previous lambda (or null)
    int    []       _activeCols;
    GLMGradientInfo _ginfo;      // gradient and penalty of glm + L2 pen.transient double [] _activeBeta;
    double          _objVal;     // full objective value including L1 pen
    int             _iter;
    int             _workPerIteration;
    int             _workPerLambda;
    int             _worked;     // total number of worked units
    double          _nullGradNorm;
    double          _nullDevTrain;
    double          _resDevTest = Double.NaN;
    int             _stopCnt; // count of subsequent lambdas with worsening deviance
    boolean         _scoredAndUpdated;

    // these are not strictly state variables
    // I put them here to have all needed info in state object (so I only need to keep State[] info when doing xval)
    final Key             _dstKey;
    boolean _allIn;

    // vecs used by cooridnate descent
    Vec _eVec; // eta
    Vec _wVec; // weights
    Vec _zVec; // z
    Vec _iVec; // intercept - all 1s
    final int _fullN;
    public boolean _lineSearch;
    public int _lsCnt;

    public GLMTaskInfo(Key dstKey, int foldId, long nobs, double wsum, double ymu, double lmax, double[] beta, int fullN, GLMGradientInfo ginfo, double objVal){
      _dstKey = dstKey;
      _foldId = foldId;
      _nobs = nobs;
      _wsum = wsum;
      _ymu = ymu;
      _lambdaMax = lmax;
      _beta = beta;
      _ginfo = ginfo;
      _objVal = objVal;
      _fullN = fullN;
    }


    public double gradientCheck(double lambda, double alpha){
      // assuming full-gradient, beta only for active columns
      double [] beta = _beta;
      double [] subgrad = _ginfo._gradient.clone();
      double err = 0;
      ADMM.subgrad(alpha*lambda,beta,subgrad);
      for(double d: subgrad)
        if(err < -d) err = -d; else if(err < d) err = d;
      return err;
    }
    public void adjustToNewLambda( double currentLambda, double newLambda, double alpha, boolean intercept) {
      assert newLambda < currentLambda:"newLambda = " + newLambda + ", last lambda = " + currentLambda;
      double ldiff = (newLambda - currentLambda);
      double l2pen = .5 * (1-alpha) * ArrayUtils.l2norm2(_beta, intercept);
      double l1pen = alpha * ArrayUtils.l1norm(_beta, intercept);
      for (int i = 0; i < _ginfo._gradient.length - (intercept?1:0); ++i)
        _ginfo._gradient[i] += ldiff * (1-alpha) * _beta[i];
      _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal + ldiff * l2pen, _ginfo._gradient);
      _objVal = _objVal + ldiff * (l1pen + l2pen); //todo add proximal penalty?
    }
  }

  private final double lmax(GLMGradientTask gLmax) {
    return Math.max(ArrayUtils.maxValue(gLmax._gradient),-ArrayUtils.minValue(gLmax._gradient))/Math.max(1e-2,_parms._alpha[0]);
  }

  /**
   * Contains implementation of the glm algo.
   * It's a DTask so it can be computed on other nodes (to distributed single node part of the computation).
   */
  public final class GLMDriver extends DTask<GLMDriver> {
    transient AtomicBoolean _gotException = new AtomicBoolean();

    public GLMDriver(H2OCountedCompleter cmp){ super(cmp);}

    private void doCleanup(){
      try {
        _parms.read_unlock_frames(GLM.this);
      }
      catch (Throwable t) {
        // nada
      }
      if (null != _dinfo)
        DKV.remove(_dinfo._key);
      if(_validDinfo != null)
        DKV.remove(_validDinfo._key);
      if(_rowFilter != null)
        _rowFilter.remove();
      if(_tInfos != null && _tInfos[0] != null) {
        if (_tInfos[0]._wVec != null)
          _tInfos[0]._wVec.remove();
        if (_tInfos[0]._zVec != null)
          _tInfos[0]._zVec.remove();
        if (_tInfos[0]._eVec != null)
          _tInfos[0]._eVec.remove();
        if (_tInfos[0]._iVec != null)
          _tInfos[0]._iVec.remove();
      }
    }
    @Override public void onCompletion(CountedCompleter cc) {
      _model.unlock(GLM.this._key);
      doCleanup();
      done();
    }
    @Override public boolean onExceptionalCompletion(final Throwable ex, CountedCompleter cc){
      if(!_gotException.getAndSet(true)){
        if( ex instanceof JobCancelledException) {
          GLM.this.cancel();
          tryComplete();
          return false;
        }
        if(ex instanceof TooManyPredictorsException){
          // TODO add a warning
          tryComplete();
          return false;
        }
        try {
          doCleanup();
          new RemoveCall(null, _dest).invokeTask();
        } catch(Throwable t) {Log.err(t);}
        failed(ex);
        return true;
      }
      return false;
    }

    @Override
    protected void compute2() {
      init(true);
      // GLMModel(Key selfKey, GLMParameters parms, GLMOutput output, DataInfo dinfo, double ymu, double lambda_max, long nobs, float [] thresholds) {
      if (error_count() > 0) {
        GLM.this.updateValidationMessages();
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
      }
      _parms.read_lock_frames(GLM.this);
      if(_parms._n_folds != 0)
        throw H2O.unimpl();
      //todo: fill in initialization for n-folds
      new GLMSingleLambdaTsk(new LambdaSearchIteration(this),_tInfos[0]).fork();
    }
    private class LambdaSearchIteration extends H2O.H2OCallback {
      public LambdaSearchIteration(H2OCountedCompleter cmp){super(cmp); }
      @Override
      public void callback(H2OCountedCompleter h2OCountedCompleter) {
        assert _tInfos[0]._ginfo._gradient.length == _dinfo.fullN() + 1;
        int rank = 0;
        for (int i = 0; i < _tInfos[0]._beta.length - (_dinfo._intercept ? 1 : 0); ++i)
          if (_tInfos[0]._beta[i] != 0) ++rank;
        Log.info("Solution at lambda = " + _parms._lambda[_lambdaId] + " has " + rank + " nonzeros, gradient err = " + _tInfos[0].gradientCheck(_parms._lambda[_lambdaId], _parms._alpha[0]));
        update(_tInfos[0]._workPerLambda, "lambda = " + _lambdaId + ", iteration = " + _tInfos[0]._iter + ", got " + rank + "nonzeros");
        // launch next lambda
        ++_lambdaId;
//        if (_parms._lambda_search && !_parms._exactLambdas && _lambdaId == _parms._lambda.length && _tInfos[0]._stopCnt == 0) {
//          _parms._lambda = Arrays.copyOf(_parms._lambda, _parms._lambda.length + 1);
//          _parms._lambda[_parms._lambda.length - 1] = _parms._lambda[_parms._lambda.length - 2] * .9;
//        }
        if (_tInfos[0]._iter < _parms._max_iterations && _lambdaId < _parms._lambda.length && _tInfos[0]._stopCnt < 3 ) {
          double currentLambda = _parms._lambda[_lambdaId - 1];
          double nextLambda = _parms._lambda[_lambdaId];
          _tInfos[0].adjustToNewLambda(currentLambda, nextLambda, _parms._alpha[0], _dinfo._intercept);
          getCompleter().addToPendingCount(1);
          new GLMSingleLambdaTsk(new LambdaSearchIteration((H2OCountedCompleter) getCompleter()), _tInfos[0]).fork();
        }
      }
    }
  }

//  private void setSubmodel(double[] fullBeta, double explainedDevTrain, double explainedDevHoldOut, boolean score, final H2OCountedCompleter cmp) {
//    final int iter = _tInfos[0]._iter;
//    final double [] fb = MemoryManager.arrayCopyOf(fullBeta,fullBeta.length);
//    if(_parms._intercept)
//      fb[fb.length-1] += _iceptAdjust;
//    final Submodel sm = new Submodel(_parms._lambda[_lambdaId],fullBeta, iter, explainedDevTrain, explainedDevHoldOut);
//    H2O.submitTask(new H2OCountedCompleter(cmp) {
//      @Override
//      protected void compute2() {
//        new UpdateGLMModelTsk(this,sm).fork(_dest);
//      }
//    });
//  }

  double objVal(double likelihood, double[] beta, double lambda, long nobs, boolean intercept) {
    double alpha = _parms._alpha[0];
    double proximalPen = 0;
    if (_bc._betaGiven != null) {
      for (int i = 0; i < _bc._betaGiven.length; ++i) {
        double diff = beta[i] - _bc._betaGiven[i];
        proximalPen += diff * diff * _bc._rho[i] * .5;
      }
    }
    return likelihood / nobs
      + proximalPen
      + lambda * (alpha * ArrayUtils.l1norm(beta, intercept)
      + (1 - alpha) * .5 * ArrayUtils.l2norm2(beta, intercept));
  }

  /**
   * Task to compute GLM solution for a particular (single) lambda value.
   * Can be warm-started by passing in a state of previous computation so e.g. incremental strong rules can be
   * applied.
   *
   * The performs iterative reweighted least squares algorithm with elastic net penalty.
   *
   */
  public final class GLMSingleLambdaTsk extends DTask<GLMSingleLambdaTsk> {
    DataInfo _activeData;
    GLMTaskInfo _taskInfo;
    long _start_time;

    public GLMSingleLambdaTsk(H2OCountedCompleter cmp, GLMTaskInfo state) {
      super(cmp);
      _taskInfo = state;
      assert DKV.get(_dinfo._key) != null;
    }
    private String LogInfo(String msg) {
      msg = "GLM[dest=" + _taskInfo._dstKey + ", iteration=" + _taskInfo._iter + ", lambda = " + MathUtils.roundToNDigits(_parms._lambda[_lambdaId],4) + "]: " + msg;
      Log.info(msg);
      return msg;
    }
    private String LogDebug(String msg) {
      msg = "GLM[dest=" + _taskInfo._dstKey + ", iteration=" + _taskInfo._iter + ", lambda = " + _parms._lambda[_lambdaId] + "]: " + msg;
      Log.debug(msg);
      return msg;
    }
    /**
     * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
     *
     * @return indices of expected active predictors.
     */
    private int[] activeCols(final double l1, final double l2, final double[] grad) {
      if (_taskInfo._allIn) return null;
      int selected = 0;
      int[] cols = null;
      if (_parms._alpha[0] > 0) {
        final double rhs = _parms._alpha[0] * (2 * l1 - l2);
        cols = MemoryManager.malloc4(_dinfo.fullN());
        int j = 0;
        int [] oldActiveCols = _taskInfo._activeCols;
        if (oldActiveCols == null) oldActiveCols = new int[0];
        for (int i = 0; i < _dinfo.fullN(); ++i)
          if ((j < oldActiveCols.length && i == oldActiveCols[j]) || grad[i] > rhs || grad[i] < -rhs) {
            cols[selected++] = i;
            if (j < oldActiveCols.length && i == oldActiveCols[j]) ++j;
          }
      }
      if (_parms._alpha[0] == 0 || selected == _dinfo.fullN()) {
        _taskInfo._allIn = true;
        _activeData = _dinfo;
        LogInfo("All " + _dinfo.fullN() + " coefficients are active");
        return null;
      } else {
        LogInfo(selected + " / " + _dinfo.fullN() + " cols are active");
        return Arrays.copyOf(cols, selected);
      }
    }


    private void doUpdateCD(double [] grads, double [][] xx, double [] betaold, double [] betanew , int variable) {

      double diff = betaold[variable] - betanew[variable];
      double [] ary = xx[variable];

      for(int i = 0; i < grads.length; i++) {
        if (i != variable) {// variable is index of most recently updated
          grads[i] += diff * ary[i];
        }
      }

    }

    protected void solve(boolean doLineSearch){
      if (_activeData.fullN() > _parms._max_active_predictors)
        throw new TooManyPredictorsException();
      Solver solverType = _parms._solver;
      if(solverType == Solver.AUTO)
        if(_activeData.fullN() > 6000 || _activeData._adaptedFrame.numCols() > 500)
          solverType = Solver.L_BFGS;
        else
          solverType = Solver.IRLSM; // default choice
      switch(solverType) {
        case L_BFGS: {
          double[] beta = _taskInfo._beta;
          assert beta.length == _activeData.fullN()+1;
          GradientSolver solver = new GLMGradientSolver(_parms, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _taskInfo._ymu, _taskInfo._wsum, _rowFilter);
          if(_bc._betaGiven != null && _bc._rho != null)
            solver = new ProximalGradientSolver(solver,_bc._betaGiven,_bc._rho);
          if (beta == null) {
            beta = MemoryManager.malloc8d(_activeData.fullN() + (_activeData._intercept ? 1 : 0));
            if (_activeData._intercept)
              beta[beta.length - 1] = _parms.link(_taskInfo._ymu);
          }
          L_BFGS lbfgs = new L_BFGS().setMinIter(5).setObjEps(_parms._objective_epsilon).setGradEps(_parms._gradient_epsilon).setMaxIter(_parms._max_iterations);
          assert beta.length == _taskInfo._ginfo._gradient.length;
          final double l1pen = _parms._lambda[_lambdaId] * _parms._alpha[0];
          if(l1pen > 0 || _bc.hasBounds()) {
            // compute gradient at null beta to get estimate for rho
            double [] nullBeta = MemoryManager.malloc8d(_taskInfo._beta.length);
            if(_dinfo._intercept)
              nullBeta[nullBeta.length-1] = _parms.link(_taskInfo._ymu);
            double [] g = solver.getGradient(nullBeta)._gradient;
            ArrayUtils.mult(g,100);
            double [] rho = MemoryManager.malloc8d(beta.length);
            // compute rhos
            for(int i = 0; i < rho.length - 1; ++i)
              rho[i] = ADMM.L1Solver.estimateRho(-g[i], l1pen, _bc._betaLB == null?Double.NEGATIVE_INFINITY:_bc._betaLB[i], _bc._betaUB == null?Double.POSITIVE_INFINITY:_bc._betaUB[i]);
            int ii = rho.length - 1;
            rho[ii] = ADMM.L1Solver.estimateRho(-g[ii], 0, _bc._betaLB == null?Double.NEGATIVE_INFINITY:_bc._betaLB[ii], _bc._betaUB == null?Double.POSITIVE_INFINITY:_bc._betaUB[ii]);
            for(int i = 0; i < rho.length-1; ++i)
              rho[i] = Math.min(1000,rho[i]);
            final double [] objvals = new double[2];
            objvals[1] = Double.POSITIVE_INFINITY;
            L_BFGS.ProgressMonitor pm = new L_BFGS.ProgressMonitor(){
              public boolean progress(double [] beta, GradientInfo ginfo){
                double obj = ginfo._objVal;
                double gnorm = -1;
                if(ginfo instanceof ProximalGradientInfo) {
                  ProximalGradientInfo g = (ProximalGradientInfo)ginfo;
                  if(g._origGinfo instanceof GLMGradientInfo) {
                    GLMGradientInfo gg = (GLMGradientInfo)g._origGinfo;
                    obj = gg._objVal;
                    for(int i = 0; i < beta.length; ++i)
                      obj += l1pen * (beta[i] >= 0?beta[i]:-beta[i]);
                    // add l1pen
                    _sc.addIterationScore(_taskInfo._iter, gg._likelihood, obj);
                    double [] subgrad = ginfo._gradient.clone();
                    ADMM.subgrad(l1pen,beta,subgrad);
                    gnorm = ArrayUtils.l2norm2(subgrad,false);
                  }
                }
                if((++_taskInfo._iter & 3) == 0)
                  update(_taskInfo._workPerIteration*4, "iteration " + _taskInfo._iter+ ", objective = " + MathUtils.roundToNDigits(obj,4) + ", grad_norm = " + MathUtils.roundToNDigits(gnorm,6), GLM.this._key);
                return _taskInfo._iter < _parms._max_iterations && Job.isRunning(GLM.this._key);
              }
            };

            double reltol = L1Solver.DEFAULT_RELTOL;
            double abstol = L1Solver.DEFAULT_ABSTOL;
            double ADMM_gradEps = 1e-2;
            if(_bc != null)
              new ADMM.L1Solver(ADMM_gradEps, 500, reltol, abstol).solve(new LBFGS_ProximalSolver(solver,_taskInfo._beta,rho, pm).setObjEps(_parms._objective_epsilon).setGradEps(_parms._gradient_epsilon), _taskInfo._beta, l1pen, _activeData._intercept, _bc._betaLB, _bc._betaUB);
            else
              new ADMM.L1Solver(ADMM_gradEps, 500, reltol, abstol).solve(new LBFGS_ProximalSolver(solver, _taskInfo._beta, rho, pm).setObjEps(_parms._objective_epsilon).setGradEps(_parms._gradient_epsilon), _taskInfo._beta, l1pen);
          } else {
            Result r = lbfgs.solve(solver, beta, _taskInfo._ginfo, new L_BFGS.ProgressMonitor() {
              @Override
              public boolean progress(double[] beta, GradientInfo ginfo) {
                if(ginfo instanceof GLMGradientInfo) {
                  GLMGradientInfo gginfo = (GLMGradientInfo) ginfo;
                  _sc.addIterationScore(_taskInfo._iter, gginfo._likelihood, gginfo._objVal);
                }
                if ((_taskInfo._iter & 7) == 0) {
                  _taskInfo._worked += _taskInfo._workPerIteration*8;
                  update(_taskInfo._workPerIteration*8, "iteration " + (_taskInfo._iter + 1) + ", objective value = " + MathUtils.roundToNDigits(ginfo._objVal,4) + ", gradient norm = " + MathUtils.roundToNDigits(ArrayUtils.l2norm2(ginfo._gradient, false), 4), GLM.this._key);
                  LogInfo("LBFGS: objval = " + ginfo._objVal);
                }
                ++_taskInfo._iter;
                // todo update the model here so we can show intermediate results
                return isRunning(GLM.this._key);
              }
            });
            _taskInfo._beta = r.coefs;
          }
          break;
        }
        case COORDINATE_DESCENT_NAIVE: {

          int p = _activeData.fullN()+ 1;
          double wsum,wsumu; // intercept denum
          double [] denums;
          boolean skipFirstLevel = !_activeData._useAllFactorLevels;
          double [] beta =  _taskInfo._beta.clone(); // Warm start for vector with active columns only.
          double [] betaold = _taskInfo._beta.clone();
          double objold = _taskInfo._objVal;
          int iter2=0; // total cd iters

          // get reweighted least squares vectors
          Vec[] newVecs = _activeData._adaptedFrame.anyVec().makeZeros(3);
          Vec w = newVecs[0]; // fixed before each CD loop
          Vec z = newVecs[1]; // fixed before each CD loop
          Vec zTilda = newVecs[2]; // will be updated at every variable within CD loop
          long startTimeTotalNaive = System.currentTimeMillis();

          // generate new IRLS iteration
          while (iter2++ < 30) {

            Frame fr = new Frame(_activeData._adaptedFrame);
            fr.add("w", w); // fr has all data
            fr.add("z", z);
            fr.add("zTilda", zTilda);
            fr.add("filter", _rowFilter); //rows with nas to be skipped

            GLMGenerateWeightsTask gt = new GLMGenerateWeightsTask(GLM.this._key, _activeData, _parms, beta).doAll(fr);
            double objVal = objVal(gt._likelihood, gt._betaw, _parms._lambda[_lambdaId], _taskInfo._nobs, _activeData._intercept);
            denums = gt.denums;
            wsum = gt.wsum;
            wsumu = gt.wsumu;
            int iter1 = 0;

            // coordinate descent loop
            while (iter1++ < 300) {
              Frame fr2 = new Frame();
              fr2.add("w", w);
              fr2.add("z", z);
              fr2.add("zTilda", zTilda); // original x%*%beta if first iteration
              fr2.add("filter", _rowFilter); // na row to skip

              for(int i=0; i < _activeData._cats; i++) {
                Frame fr3 = new Frame(fr2);
                int level_num = _activeData._catOffsets[i+1]-_activeData._catOffsets[i];
                int prev_level_num = 0;
                fr3.add("xj", _activeData._adaptedFrame.vec(i));

                boolean intercept = (i == 0); // prev var is intercept
                if(!intercept) {
                  prev_level_num = _activeData._catOffsets[i]-_activeData._catOffsets[i-1];
                  fr3.add("xjm1", _activeData._adaptedFrame.vec(i-1)); // add previous categorical variable
                }

                int start_old = _activeData._catOffsets[i];
                GLMCoordinateDescentTaskSeqNaive stupdate;
                if(intercept)
                  stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, 4 , Arrays.copyOfRange(betaold, start_old, start_old+level_num),
                        new double [] {beta[p-1]}, _activeData._catLvls[i], null, null, null, null, null, skipFirstLevel).doAll(fr3);
                else
                  stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, 1 , Arrays.copyOfRange(betaold, start_old,start_old+level_num),
                          Arrays.copyOfRange(beta, _activeData._catOffsets[i-1], _activeData._catOffsets[i]) ,  _activeData._catLvls[i] ,
                          _activeData._catLvls[i-1], null, null, null, null, skipFirstLevel ).doAll(fr3);

                for(int j=0; j < level_num; ++j)
                 beta[_activeData._catOffsets[i]+j] = ADMM.shrinkage(stupdate._temp[j] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                         / (denums[_activeData._catOffsets[i]+j] / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
              }

              int cat_num = 2; // if intercept, or not intercept but not first numeric, then both are numeric .
              for (int i = 0; i < _activeData._nums; ++i) {
                GLMCoordinateDescentTaskSeqNaive stupdate;
                Frame fr3 = new Frame(fr2);
                fr3.add("xj", _activeData._adaptedFrame.vec(i+_activeData._cats)); // add current variable col
                boolean intercept = (i == 0 && _activeData.numStart() == 0); // if true then all numeric case and doing beta_1

                double [] meannew=null, meanold=null, varnew=null, varold=null;
                if(i > 0 || intercept) {// previous var is a numeric var
                    cat_num = 3;
                    if(!intercept)
                     fr3.add("xjm1", _activeData._adaptedFrame.vec(i - 1 + _activeData._cats)); // add previous one if not doing a beta_1 update, ow just pass it the intercept term
                  if( _activeData._normMul!=null ) {
                    varold = new double[]{_activeData._normMul[i]};
                    meanold = new double[]{_activeData._normSub[i]};
                    if (i!= 0){
                      varnew = new double []{ _activeData._normMul[i-1]};
                      meannew = new double [] { _activeData._normSub[i-1]};
                    }
                  }
                  stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, cat_num , new double [] { betaold[_activeData.numStart()+ i]},
                              new double []{ beta[ (_activeData.numStart()+i-1+p)%p ]}, null, null,
                             varold, meanold, varnew, meannew, skipFirstLevel ).doAll(fr3);

                    beta[i+_activeData.numStart()] = ADMM.shrinkage(stupdate._temp[0] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                            / (denums[i+_activeData.numStart()] / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
                   }
                else if (i == 0 && !intercept){ // previous one is the last categorical variable
                    int prev_level_num = _activeData.numStart()-_activeData._catOffsets[_activeData._cats-1];
                    fr3.add("xjm1", _activeData._adaptedFrame.vec(_activeData._cats-1)); // add previous categorical variable
                    if( _activeData._normMul!=null){
                      varold = new double []{ _activeData._normMul[i]};
                      meanold =  new double [] { _activeData._normSub[i]};
                    }
                    stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, cat_num , new double [] {betaold[ _activeData.numStart()]},
                            Arrays.copyOfRange(beta,_activeData._catOffsets[_activeData._cats-1],_activeData.numStart() ), null, _activeData._catLvls[_activeData._cats-1],
                            varold, meanold, null, null, skipFirstLevel ).doAll(fr3);
                    beta[_activeData.numStart()] = ADMM.shrinkage(stupdate._temp[0] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                            / (denums[_activeData.numStart()] / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
                  }
              }

              // intercept update: preceded by a categorical or numeric variable
              Frame fr3 = new Frame(fr2);
              fr3.add("xjm1", _activeData._adaptedFrame.vec( _activeData._cats + _activeData._nums-1 ) ); // add last variable updated in cycle to the frame
              GLMCoordinateDescentTaskSeqNaive iupdate ;
              if( _activeData._adaptedFrame.vec( _activeData._cats + _activeData._nums-1).isEnum()) { // only categorical vars
                cat_num = 2;
                iupdate = new GLMCoordinateDescentTaskSeqNaive( false, true, cat_num , new double [] {betaold[betaold.length-1]},
                        Arrays.copyOfRange(beta, _activeData._catOffsets[_activeData._cats-1], _activeData._catOffsets[_activeData._cats] ),
                        null, _activeData._catLvls[_activeData._cats-1], null, null, null, null, skipFirstLevel  ).doAll(fr3);
              }
              else { // last variable is numeric
                cat_num = 3;
                double [] meannew=null, varnew=null;
                if(_activeData._normMul!=null){
                  varnew = new double [] {_activeData._normMul[_activeData._normMul.length-1]};
                  meannew = new double [] {_activeData._normSub[_activeData._normSub.length-1]};
                }
                iupdate = new GLMCoordinateDescentTaskSeqNaive(false, true, cat_num ,
                        new double [] {betaold[betaold.length-1]}, new double []{ beta[beta.length-2] }, null, null,
                        null, null, varnew, meannew , skipFirstLevel ).doAll(fr3);
              }
              if(_parms._intercept)
               beta[beta.length - 1] = iupdate._temp[0] / wsum;

              double maxdiff = ArrayUtils.linfnorm(ArrayUtils.subtract(beta, betaold), false); // false to keep the intercept
              System.arraycopy(beta, 0, betaold, 0, beta.length);
              if (maxdiff < _parms._beta_epsilon)
                break;
            }

            double percdiff = Math.abs((objold - objVal)/objold);
            if (percdiff < _parms._objective_epsilon & iter2 >1 )
              break;
            objold=objVal;

            _taskInfo._beta = beta.clone();
            System.out.println("iter1 = " + iter1);

            //   for (int i = 0 ; i < beta.length; ++i) {
            //     System.out.print(beta[i] + " ");
            //   }
            //   System.out.println();

          }

          System.out.println("iter2 = " + iter2);

          long endTimeTotalNaive = System.currentTimeMillis();
          long durationTotalNaive = (endTimeTotalNaive - startTimeTotalNaive)/1000;
          System.out.println("Time to run Naive Coordinate Descent " + durationTotalNaive);
          _taskInfo._iter = iter2;
          for (Vec v : newVecs) v.remove();
          break;
        }

        case COORDINATE_DESCENT: {

          int p = _activeData.fullN()+ 1;
          double wsum,wsumu; // intercept denum
          boolean skipFirstLevel = !_activeData._useAllFactorLevels;
          double[] beta =  _taskInfo._beta.clone(); // Warm start for vector with active columns only.
          double[] betaold = _taskInfo._beta.clone();
          int iter2=0; // total cd iters
          double objold = _taskInfo._objVal;

          long startTimeTotalCov = System.currentTimeMillis();

          // new IRLS iteration
          while (iter2++ < 30) {
            long startTimeCov = System.currentTimeMillis();
            GLMIterationTask gt = new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId], _parms,
                    false, _taskInfo._beta, _parms._intercept?_taskInfo._ymu:0.5, _rowFilter,
                    null).doAll(_activeData._adaptedFrame);
            long endTimeCov = System.currentTimeMillis();
            long durationCov = (endTimeCov - startTimeCov)/1000;
            System.out.println("Time to compute cov matrix " + durationCov);

            double objVal = objVal(gt._likelihood, gt._beta, _parms._lambda[_lambdaId], _taskInfo._nobs, _activeData._intercept);
            wsum = gt.wsum;
            wsumu = gt.wsumu;
            int iter1 = 0;
            double [] grads = Arrays.copyOfRange(gt._xy,0,gt._xy.length );//-1 // initialize to inner ps with observations
            for(int i = 0; i < grads.length; ++i) {
              double ip = 0;
              for(int j = 0; j < beta.length; ++j)
                ip += beta[j]*gt._gram.get(i,j);
              grads[i] = grads[i] - ip + beta[i]*gt._gram.get(i,i);
            }
            long t1 = System.currentTimeMillis();
            long startTimeCd = System.currentTimeMillis();
            double [][] XX = gt._gram.getXX();
            // CD loop
            while (iter1++ < 300) {

              for(int i=0; i < _activeData._cats; ++i) {
                int level_num = _activeData._catOffsets[i+1]-_activeData._catOffsets[i];
                int off = _activeData._catOffsets[i];
                for(int j=off; j < off + level_num; ++j) { // ST multiple ones at the same time.
                  if (gt._gram.get(j, j) != 0)
                    beta[j] = ADMM.shrinkage(grads[j] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                            / (gt._gram.get(j, j) / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
                  else
                    beta[j] = 0;
                  if( beta[j] != 0 )
                    doUpdateCD(grads, XX, betaold, beta, j);
                }

              }

              int off = _activeData.numStart();
              for (int i = off; i < _activeData._nums + off; ++i) {
                if(gt._gram.get(i,i)!= 0)
                   beta[i] = ADMM.shrinkage(grads[i] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                          / (gt._gram.get(i,i) / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
                else
                  beta[i]=0;

                if(beta[i]!=0) // update all the grad entries
                    doUpdateCD(grads, XX, betaold, beta, i);
              }

              if(_parms._intercept) {
                beta[beta.length - 1] = grads[grads.length - 1] / wsum;
                if (beta[beta.length - 1] != 0) // update all the grad entries
                  doUpdateCD(grads, XX, betaold, beta, beta.length - 1);
              }
              double maxdiff = ArrayUtils.linfnorm(ArrayUtils.subtract(beta, betaold), false); // false to keep the intercept
              System.arraycopy(beta, 0, betaold, 0, beta.length);
              if (maxdiff < _parms._beta_epsilon)
                break;
            }
            long endTimeCd = System.currentTimeMillis();
            long durationCd = (endTimeCd - startTimeCd);
            System.out.println("Time to run inner CD " + durationCd/1000);
            System.out.println("inner loop done in " + iter1 + " iterations and " + (System.currentTimeMillis()-t1)/1000 + "s, iter2 = " + iter2);

            double percdiff = Math.abs((objold-objVal)/objold);
            objold=objVal;
            _taskInfo._beta = beta.clone();
            if (percdiff < _parms._objective_epsilon & iter2 >1 )
              break;

          }

          long endTimeTotalCov = System.currentTimeMillis();
          long durationTotalCov = (endTimeTotalCov - startTimeTotalCov)/1000;
          System.out.println("Time to run Cov Updates Coordinate Descent " + durationTotalCov);
          _taskInfo._iter = iter2;
          break;
        }



//        case COORDINATE_DESCENT:
//          double l1pen = _parms._alpha[0]*_parms._lambda[_lambdaId];
//          double l2pen = (1-_parms._alpha[0])*_parms._lambda[_lambdaId];
//          double [] beta = _taskInfo._beta.clone();
//          int off;
//          double xOldSub;
//          double xOldMul;
//          double xNewSub = 0;
//          double xNewMul = 1;
//          double [] betaUpdate = null;
//          boolean betaChanges = true;
//          int iter = 0;
//          // external loop - each time generate weights based on previous beta, compute new beta as solution to weighted least squares
//          while(betaChanges) {
//            // internal loop - go over each column independently as long as beta keeps changing
//            int it = iter; // to keep track of inner iterations
//            while (betaChanges && ++iter < 1000) {
//              betaChanges = false;
//              // run one iteration of coordinate descent - go over all columns
//              for (int i = 0; i < _activeData._adaptedFrame.numCols(); ++i) {
//                Vec previousVec = i == 0?_taskInfo._iVec:_dinfo._adaptedFrame.vec(i-1);
//                Vec currentVec = i == _dinfo._adaptedFrame.numCols()-1?_taskInfo._iVec:_dinfo._adaptedFrame.vec(i);
//                xOldSub = xNewSub;
//                xOldMul = xNewMul;
//                boolean isCategorical = currentVec.isEnum();
//                int to;
//                if (isCategorical) {
//                  xNewSub = 0;
//                  xNewMul = 1;
//                  off = _dinfo._catOffsets[i];
//                  to = _dinfo._catOffsets[i + 1];
//                } else {
//                  int k = i - _dinfo._cats;
//                  xNewSub = _dinfo._normSub[k];
//                  xNewMul = _dinfo._normMul[k];
//                  off = _dinfo.numStart() + k;
//                  to = off + 1;
//                }
//                double[] currentBeta = Arrays.copyOfRange(_taskInfo._beta, off, to);
//                double[] xy = new GLMCoordinateDescentTask(betaUpdate, currentBeta, xOldSub, xOldMul, xNewSub, xNewMul).doAll(previousVec,currentVec,_taskInfo._eVec,_taskInfo._wVec, _taskInfo._zVec)._xy;
//                for (int j = 0; j < xy.length; ++j) {
//                  betaUpdate = currentBeta;
//                  double updatedCoef = ADMM.shrinkage(xy[j], l1pen) / (1 + l2pen);
//                  betaUpdate[j] = updatedCoef - currentBeta[j];
//                  if (betaUpdate[j] < -1e-4 || betaUpdate[j] > 1e-4)
//                    betaChanges = true;
//                  beta[off + j] = updatedCoef;
//                }
//              }
//            }
//            if(iter > it+1) {
//              betaChanges = true; // beta changed during inner iteration
//              // generate new weights
//              new GLMTask.GLMWeightsTask(_parms).doAll(_dinfo._adaptedFrame.lastVec(), _taskInfo._zVec, _taskInfo._wVec, _taskInfo._eVec);
//            }
//          }
//          // done, compute the gradient and check KKTs
//          break;
        case IRLSM:// fork off ADMM iteration
          new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, false, _taskInfo._beta, _parms._intercept?_taskInfo._ymu:0.5, _rowFilter, new Iteration(this, doLineSearch)).asyncExec(_activeData._adaptedFrame);
          return;
        default:
          throw H2O.unimpl();
      }
      checkKKTsAndComplete(true);
      tryComplete();
    }
    // Compute full gradient gradient (including inactive columns) and check KKT conditions, re-solve if necessary.
    // Can't be onCompletion(), can invoke solve again
    protected void checkKKTsAndComplete(final boolean score) {
      final double [] fullBeta = expandVec(_taskInfo._beta,_activeData._activeCols,_dinfo.fullN()+1);
      fullBeta[fullBeta.length-1] += _iceptAdjust;
      addToPendingCount(1);
      _taskInfo._scoredAndUpdated = score;
      new GLMTask.GLMGradientTask(_dinfo, _parms, _parms._lambda[_lambdaId], fullBeta, 1.0 / _taskInfo._nobs, _rowFilter, new H2OCallback<GLMGradientTask>(this) {
        @Override
        public void callback(final GLMGradientTask gt1) {
          assert gt1._nobs == _taskInfo._nobs;
          double[] subgrad = gt1._gradient.clone();
          ADMM.subgrad(_parms._alpha[0] * _parms._lambda[_lambdaId], fullBeta, subgrad);
          double err = 0;
          if (_taskInfo._activeCols != null) {
            for (int c : _taskInfo._activeCols)
              if (subgrad[c] > err) err = subgrad[c];
              else if (subgrad[c] < -err) err = -subgrad[c];
            int[] failedCols = new int[64];
            int fcnt = 0;
            for (int i = 0; i < subgrad.length - 1; ++i) {
              if (Arrays.binarySearch(_taskInfo._activeCols, i) >= 0) continue;
              if (subgrad[i] > err || -subgrad[i] > err) {
                if (fcnt == failedCols.length)
                  failedCols = Arrays.copyOf(failedCols, failedCols.length << 1);
                failedCols[fcnt++] = i;
              }
            }
            if (fcnt > 0) {
              final int n = _taskInfo._activeCols.length;
              int[] newCols = Arrays.copyOf(_taskInfo._activeCols, _taskInfo._activeCols.length + fcnt);
              for (int i = 0; i < fcnt; ++i)
                newCols[n + i] = failedCols[i];
              Arrays.sort(newCols);
              _taskInfo._beta = resizeVec(gt1._beta, newCols, _taskInfo._activeCols, _dinfo.fullN() + 1);
              _taskInfo._activeCols = newCols;
              LogInfo(fcnt + " variables failed KKT conditions check! Adding them to the model and continuing computation.(grad_eps = " + err + ", activeCols = " + (_taskInfo._activeCols.length > 100 ? "lost" : Arrays.toString(_taskInfo._activeCols)));
              _activeData = _dinfo.filterExpandedColumns(_taskInfo._activeCols);
              assert newCols == null || _activeData.fullN() == _taskInfo._activeCols.length;
              // NOTE: tricky completer game here:
              // We expect 0 pending in this method since this is the end-point, ( actually it's racy, can be 1 with pending 1 decrement from the original Iteration callback, end result is 0 though)
              // while iteration expects pending count of 1, so we need to increase it here (Iteration itself adds 1 but 1 will be subtracted when we leave this method since we're in the callback which is called by onCompletion!
              // [unlike at the start of nextLambda call when we're not inside onCompletion]))
              getCompleter().addToPendingCount(1);
              solve(true);
              return;
            }
          }
          if (_valid != null) {
            GLMSingleLambdaTsk.this.addToPendingCount(1);
            final int iter = _taskInfo._iter;
            // public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, H2OCountedCompleter cc){
            new GLMTask.GLMGradientTask(_validDinfo, _parms, _parms._lambda[_lambdaId], _dinfo.denormalizeBeta(gt1._beta), 1.0 / _taskInfo._nobs, null /* no row filter for validation dataset */, new H2OCallback<GLMGradientTask>(GLMSingleLambdaTsk.this) {
              @Override
              public void callback(GLMGradientTask gt2) {
                LogInfo("hold-out set validation = " + gt2._val.toString());
                if(Double.isNaN(_taskInfo._resDevTest) || MathUtils.roundToNDigits(gt2._val.residualDeviance(),5) <= MathUtils.roundToNDigits(_taskInfo._resDevTest,5))
                  _taskInfo._stopCnt = 0;
                else ++_taskInfo._stopCnt;
                _taskInfo._resDevTest = gt2._val.residualDeviance();
                // can not use any of the member variables, since computation will go on in parallell
                // also, we have already fully expanded beta here -> different call than from in-between iterations
                Submodel sm = new Submodel(_parms._lambda[_lambdaId],gt1._beta, _taskInfo._iter,gt1._val.residualDeviance(), gt2._val.residualDeviance());
                _model.setSubmodel(sm);
                if(score) { // pick best model first and if the latest is the best, udpate the metrics
                  _model._output.pickBestModel();
                  if(_model._output.bestSubmodel().lambda_value ==  _parms._lambda[_lambdaId]) {
                    // latest is the best
                    _model._output._training_metrics = gt1._val.makeModelMetrics(_model,_parms.train(),_dinfo._adaptedFrame.lastVec().sigma());
                    _model._output._validation_metrics = gt2._val.makeModelMetrics(_model,_parms.valid(),_validDinfo._adaptedFrame.lastVec().sigma());
                  }
                  _model.generateSummary(_parms._train, _taskInfo._iter);
                  _model._output._scoring_history = _sc.to2dTable();
                  _model.update(GLM.this._key);
                }
                _sc.addLambdaScore(_taskInfo._iter,_parms._lambda[_lambdaId], sm.rank(), gt1._val.explainedDev(), gt2._val.explainedDev());
//                addLambdaScoringHistory(iter,lambdaId,rank,1 - gt1._dev/_taskInfo._nullDevTrain,1 - gt2._dev/_taskInfo._nullDevTest);
              }
            }).setValidate(_parms._intercept?_taskInfo._ymu:0, score).asyncExec(_validDinfo._adaptedFrame);
          } else {
            Submodel sm = new Submodel(_parms._lambda[_lambdaId], gt1._beta, _taskInfo._iter, gt1._val.residualDeviance(), Double.NaN);
            _model.setSubmodel(sm);
            _sc.addLambdaScore(_taskInfo._iter,_parms._lambda[_lambdaId], sm.rank(), gt1._val.explainedDev(), Double.NaN);
            if(score) { // set the training metrics (always the last iteration if running without validation set)
              _model._output.pickBestModel();
              if(_model._output.bestSubmodel().lambda_value ==  _parms._lambda[_lambdaId])
                _model._output._training_metrics = gt1._val.makeModelMetrics(_model,_parms.train(),_dinfo._adaptedFrame.lastVec().sigma());
              _model.generateSummary(_parms._train,_taskInfo._iter);
              _model._output._scoring_history = _sc.to2dTable();
              _model.update(GLM.this._key);
            }
          }
          // got valid solution, update the state and complete
          double l2pen = _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]) * ArrayUtils.l2norm2(gt1._beta, _activeData._intercept);
          if(_bc._betaGiven != null && _bc._rho != null) {
            for(int i = 0; i < _bc._betaGiven.length; ++i) {
              double diff = gt1._beta[i] - _bc._betaGiven[i];
              l2pen += _bc._rho[i] * diff * diff;
            }
          }
          l2pen *= .5;
          _taskInfo._ginfo = new GLMGradientInfo(gt1._likelihood, gt1._likelihood/gt1._nobs + l2pen, gt1._gradient);
          assert _taskInfo._ginfo._gradient.length == _dinfo.fullN() + 1:_taskInfo._ginfo._gradient.length + " != " + _dinfo.fullN() + ", intercept = " + _parms._intercept;
          _taskInfo._objVal = objVal(gt1._likelihood,gt1._beta, _parms._lambda[_lambdaId],gt1._nobs,_dinfo._intercept);
          _sc.addIterationScore(_taskInfo._iter,gt1._likelihood,_taskInfo._objVal); // it's in here for the gaussian family score :(
          _taskInfo._beta = fullBeta;
        }
      }).setValidate(_parms._intercept?_taskInfo._ymu : _parms._family == Family.binomial?0.5:0, score).asyncExec(_dinfo._adaptedFrame);
    }
    @Override
    protected void compute2() { // part of the outer loop to compute sol for lambda_k+1. keep active cols using strong rules and calls solve.
      if(!isRunning(_key)) throw new JobCancelledException();
      assert _rowFilter != null;
      _start_time = System.currentTimeMillis();

      if(Math.abs(_parms._lambda[_lambdaId] - 0.0207 ) < 0.001) // 0.030035459652215813) < 0.001) //    0.02494
              System.out.println();
     // _taskInfo._allIn = true;
      int[] activeCols = activeCols(_parms._lambda[_lambdaId], _lambdaId == 0?_taskInfo._lambdaMax:_parms._lambda[_lambdaId-1], _taskInfo._ginfo._gradient);
      _taskInfo._activeCols = activeCols;
      _activeData = _dinfo.filterExpandedColumns(activeCols);
      assert _taskInfo._activeCols == null || _taskInfo._activeCols.length == _activeData.fullN();
      _taskInfo._ginfo = new GLMGradientInfo(_taskInfo._ginfo._likelihood, _taskInfo._ginfo._objVal,contractVec(_taskInfo._ginfo._gradient,activeCols));
      _taskInfo._beta = contractVec(_taskInfo._beta,activeCols);
      assert  activeCols == null || _activeData.fullN() == activeCols.length : LogInfo("mismatched number of cols, got " + activeCols.length + " active cols, but data info claims " + _activeData.fullN());
      assert DKV.get(_activeData._key) != null;
      solve(false);
    }
    private class Iteration extends H2O.H2OCallback<GLMIterationTask> {
      public final long _iterationStartTime;
      final boolean _countIteration;
      public final boolean _doLinesearch;

      public Iteration(CountedCompleter cmp, boolean doLinesearch) {
        this(cmp, doLinesearch, true);
      }

      public Iteration(CountedCompleter cmp, boolean doLinesearch, boolean countIteration) {
        super((H2OCountedCompleter) cmp);
        _countIteration = countIteration;
        _iterationStartTime = System.currentTimeMillis();
        _doLinesearch = doLinesearch;
      }

      @Override
      public void callback(final GLMIterationTask glmt) {
        assert _parms._intercept || glmt._beta[_activeData.fullN()] == 0;
        double objVal = objVal(glmt._likelihood, glmt._beta, _parms._lambda[_lambdaId], _taskInfo._nobs, _activeData._intercept);
        if (!isRunning(GLM.this._key)) throw new JobCancelledException();
        assert glmt._nobs == _taskInfo._nobs:"got wrong number of observations, expected " + _taskInfo._nobs + ", but got " + glmt._nobs + ", got row filter?" + (glmt._rowFilter != null);
        assert _taskInfo._activeCols == null || glmt._beta == null || glmt._beta.length == (_taskInfo._activeCols.length + 1) : LogInfo("betalen = " + glmt._beta.length + ", activecols = " + _taskInfo._activeCols.length);
        assert _taskInfo._activeCols == null || _taskInfo._activeCols.length == _activeData.fullN();
        double reg = 1.0 / _taskInfo._wsum;
        glmt._gram.mul(reg);
        ArrayUtils.mult(glmt._xy, reg);
        if (_countIteration) ++_taskInfo._iter;
        long callbackStart = System.currentTimeMillis();
        double lastObjVal = _taskInfo._objVal;
        double logl = glmt._likelihood;
        if (_doLinesearch && (glmt.hasNaNsOrInf() || !((lastObjVal - objVal) > -1e-8))) {
          _taskInfo._lineSearch = true;
          // nedded line search, have to discard the last step and go again with line search
          getCompleter().addToPendingCount(1);
          LogDebug("invoking line search, objval = " + objVal + ", lastObjVal = " + lastObjVal); // todo: get gradient here?
          new GLMLineSearchTask(_activeData, _parms, 1.0 / _taskInfo._nobs, _taskInfo._beta.clone(), ArrayUtils.subtract(glmt._beta, _taskInfo._beta), LINE_SEARCH_STEP, NUM_LINE_SEARCH_STEPS, _rowFilter, new LineSearchIteration(getCompleter(),glmt._likelihood)).asyncExec(_activeData._adaptedFrame);
          return;
        } else {
          _sc.addIterationScore(_taskInfo._iter - 1, logl, objVal);
          if (lastObjVal > objVal) {
            _taskInfo._beta = glmt._beta;
            _taskInfo._objVal = objVal;
            _taskInfo._ginfo = null;
          }
        }
        final double[] newBeta = MemoryManager.malloc8d(glmt._xy.length);
        double l2pen = _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]);
        double l1pen = _parms._lambda[_lambdaId] * _parms._alpha[0];
        double defaultRho = _bc._betaLB != null || _bc._betaUB != null ? _taskInfo._lambdaMax * 1e-2 : 0;
        long tx = System.currentTimeMillis();
        // l1pen or upper/lower bounds require ADMM solver
        if (l1pen > 0 || _bc._betaLB != null || _bc._betaUB != null || _bc._betaGiven != null) {
          // double rho = Math.max(1e-4*_taskInfo._lambdaMax*_parms._alpha[0],_currentLambda*_parms._alpha[0]);
          GramSolver gslvr = new GramSolver(glmt._gram, glmt._xy, _parms._intercept, l2pen, l1pen /*, rho*/, _bc._betaGiven, _bc._rho, defaultRho, _bc._betaLB, _bc._betaUB);
          new ADMM.L1Solver(1e-4, 5000).solve(gslvr, newBeta, l1pen, _parms._intercept, _bc._betaLB, _bc._betaUB);
        } else {
          glmt._gram.addDiag(l2pen);
          new GramSolver(glmt._gram,glmt._xy,_taskInfo._lambdaMax, _parms._beta_epsilon, _parms._intercept).solve(newBeta);
        }
        LogInfo("iteration computed in " + (callbackStart - _iterationStartTime) + " + " + (System.currentTimeMillis() - tx) + " ms");
        _taskInfo._worked += _taskInfo._workPerIteration;
        update(_taskInfo._workPerIteration, "lambdaId = " + _lambdaId + ", iteration = " + _taskInfo._iter + ", objective value = " + MathUtils.roundToNDigits(objVal,4));
        if (ArrayUtils.hasNaNsOrInfs(newBeta)) {
          throw new RuntimeException(LogInfo("got NaNs and/or Infs in beta"));
        } else {
          final double bdiff = beta_diff(glmt._beta, newBeta);
          if ((_parms._family == Family.gaussian && _parms._link == Link.identity) || bdiff < _parms._beta_epsilon || _taskInfo._iter >= _parms._max_iterations) { // Gaussian is non-iterative and gradient is ADMMSolver's gradient => just validate and move on to the next lambda_value
            int diff = (int) Math.log10(bdiff);
            int nzs = 0;
            for (int i = 0; i < newBeta.length; ++i)
              if (newBeta[i] != 0) ++nzs;
            LogInfo("converged (reached a fixed point with ~ 1e" + diff + " precision), got " + nzs + " nzs");
            _taskInfo._beta = _parms._family == Family.gaussian ? newBeta : glmt._beta;
            checkKKTsAndComplete(true);
            return;
          } else { // not done yet, launch next iteration
            if(_taskInfo._lineSearch || _activeData.fullN() > 1000 || _activeData._adaptedFrame.numCols() > 100){
              getCompleter().addToPendingCount(1);
              LogDebug("invoking line search, objval = " + objVal + ", lastObjVal = " + lastObjVal); // todo: get gradient here?
              new GLMLineSearchTask(_activeData, _parms, 1.0 / _taskInfo._nobs, glmt._beta, ArrayUtils.subtract(newBeta, glmt._beta), LINE_SEARCH_STEP, NUM_LINE_SEARCH_STEPS, _rowFilter, new H2OCallback<GLMLineSearchTask>((H2OCountedCompleter)getCompleter()) {
                @Override
                public void callback(GLMLineSearchTask lst) {
                  assert lst._nobs == _taskInfo._nobs;
                  double t = 1;
                  for (int i = 0; i < lst._likelihoods.length; ++i, t *= LINE_SEARCH_STEP) {
                    double[] beta = ArrayUtils.wadd(lst._beta.clone(), lst._direction, t);
                    double newObj = objVal(lst._likelihoods[i], beta, _parms._lambda[_lambdaId],_taskInfo._nobs,_activeData._intercept);
                    if (_taskInfo._objVal - newObj > 1e-8) {
                      LogDebug("step = " + t + ",  objval = " + newObj);
                      if(t == 1) {
                        if(++_taskInfo._lsCnt == 2) { // if we do not need line search in 2 consecutive iterations turn it off
                          _taskInfo._lineSearch = false;
                          _taskInfo._lsCnt = 0;
                        }
                      } else
                        _taskInfo._lsCnt = 0;
                      getCompleter().addToPendingCount(1);
                      new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, true, beta, _parms._intercept?_taskInfo._ymu:.5, _rowFilter, new Iteration(getCompleter(), true, true)).asyncExec(_activeData._adaptedFrame);
                      return;
                    }
                  }
                  // converged
                  int nzs = 0;
                  for (int i = 0; i < glmt._beta.length; ++i)
                    if (glmt._beta[i] != 0) ++nzs;
                  LogInfo("converged (step size too small(2))");
                  _taskInfo._beta = glmt._beta;
                  checkKKTsAndComplete(true);
                }
              }).asyncExec(_activeData._adaptedFrame);
            } else {
              final boolean validate = false; // too much overhead! (_taskInfo._iter % 5) == 0;
              getCompleter().addToPendingCount(1);
              new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), glmt._params, validate, newBeta, _parms._intercept ? _taskInfo._ymu : 0.5, _rowFilter, new Iteration(getCompleter(), true)).asyncExec(_activeData._adaptedFrame);
            }
          }
        }
      }
    }

    private class LineSearchIteration extends H2O.H2OCallback<GLMLineSearchTask> {
      final double _expectedLikelihood;
      LineSearchIteration(CountedCompleter cmp, double expectedLikelihood) {
        super((H2OCountedCompleter) cmp);
        _expectedLikelihood = expectedLikelihood;
      }

      @Override
      public void callback(final GLMLineSearchTask lst) {
        assert lst._nobs == _taskInfo._nobs:lst._nobs + " != " + _taskInfo._nobs  + ", filtervec = " + (lst._rowFilter == null);
        assert (Double.isNaN(_expectedLikelihood) || Double.isInfinite(_expectedLikelihood)) || Math.abs(lst._likelihoods[0] - _expectedLikelihood)/_expectedLikelihood < 1e-6:"expected likelihood = " + _expectedLikelihood + ", got " + lst._likelihoods[0];
        double t = 1;
        for (int i = 0; i < lst._likelihoods.length; ++i, t *= LINE_SEARCH_STEP) {
          double[] beta = ArrayUtils.wadd(_taskInfo._beta.clone(), lst._direction, t);
          double newObj = objVal(lst._likelihoods[i], beta, _parms._lambda[_lambdaId],_taskInfo._nobs,_activeData._intercept);
          if (_taskInfo._objVal > newObj) {
            assert t < 1;
            LogInfo("line search: found admissible step = " + t + ",  objval = " + newObj);
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, true, beta, _parms._intercept?_taskInfo._ymu:.5, _rowFilter, new Iteration(getCompleter(), true, false)).asyncExec(_activeData._adaptedFrame);
            return;
          }
        }
        // no line step worked => converge
        LogInfo("converged (step size too small(1))");
        checkKKTsAndComplete(true);
      }
    }

    private final double beta_diff(double[] b1, double[] b2) {
      if (b1 == null) return Double.MAX_VALUE;
      double res = b1[0] >= b2[0] ? b1[0] - b2[0] : b2[0] - b1[0];
      for (int i = 1; i < b1.length; ++i) {
        double diff = b1[i] - b2[i];
        if (diff > res)
          res = diff;
        else if (-diff > res)
          res = -diff;
      }
      return res;
    }


    protected double l1norm(double[] beta) {
      if (beta == null) return 0;
      double l1 = 0;
      for (int i = 0; i < beta.length - 1; ++i)
        l1 += beta[i] < 0 ? -beta[i] : beta[i];
      return l1;
    }
  }

  /**
   * Created by tomasnykodym on 3/30/15.
   */
  public static final class GramSolver implements ProximalSolver {
    private final Gram _gram;
    private Cholesky _chol;

    private final double [] _xy;
    final double _lambda;
    double  [] _rho;
    boolean _addedL2;
    double _betaEps;

    private static double boundedX(double x, double lb, double ub) {
      if(x < lb)x = lb;
      if(x > ub)x = ub;
      return x;
    }

    public GramSolver(Gram gram, double[] xy, double lmax, double betaEps, boolean intercept) {
      _gram = gram;
      _lambda = 0;
      _betaEps = betaEps;
      if(!intercept) {
        gram.dropIntercept();
        xy = Arrays.copyOf(xy,xy.length-1);
      }
      _xy = xy;
      double [] rhos = MemoryManager.malloc8d(xy.length);
      computeCholesky(gram,rhos, lmax*1e-8);
      _addedL2 = rhos[0] != 0;
      _rho = _addedL2?rhos:null;
    }
    // solve non-penalized problem
    public void solve(double [] result) {
      System.arraycopy(_xy,0,result,0, _xy.length);
      _chol.solve(result);
      double gerr = Double.POSITIVE_INFINITY;
      if(_addedL2) { // had to add l2-pen to turn the gram to be SPD
        double [] oldRes = MemoryManager.arrayCopyOf(result, result.length);
        for(int i = 0; i < 1000; ++i) {
          solve(oldRes, result);
          double [] g = gradient(result);
          gerr = Math.max(-ArrayUtils.minValue(g), ArrayUtils.maxValue(g));
          if(gerr < 1e-4) return;
          System.arraycopy(result,0,oldRes,0,result.length);
        }
        Log.warn("Gram solver did not converge, gerr = " + gerr);
      }
    }

    public GramSolver(Gram gram, double[] xy, boolean intercept, double l2pen, double l1pen, double[] beta_given, double[] proxPen, double default_rho, double[] lb, double[] ub) {
      if(ub != null && lb != null)
        for(int i = 0; i < ub.length; ++i) {
          assert ub[i] >= lb[i]:i + ": ub < lb, ub = " + Arrays.toString(ub) + ", lb = " + Arrays.toString(lb) ;
        }
      _lambda = l2pen;
      _gram = gram;
      // Try to pick optimal rho constant here used in ADMM solver.
      //
      // Rho defines the strength of proximal-penalty and also the strentg of L1 penalty aplpied in each step.
      // Picking good rho constant is tricky and greatly influences the speed of convergence and precision with which we are able to solve the problem.
      //
      // Intuitively, we want the proximal l2-penalty ~ l1 penalty (l1 pen = lambda/rho, where lambda is the l1 penalty applied to the problem)
      // Here we compute the rho for each coordinate by using equation for computing coefficient for single coordinate and then making the two penalties equal.
      //
      int ii = intercept?1:0;
      int icptCol = xy.length-1;
      double [] rhos = MemoryManager.malloc8d(xy.length - 1 + ii);
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < xy.length - 1; ++i) {
        double d = xy[i];
        d = d >= 0 ? d : -d;
        if (d < min && d != 0) min = d;
      }
      double ybar = xy[icptCol];
      for (int i = 0; i < rhos.length - ii; ++i) {
        double y = xy[i];
        if (y == 0) y = min;
        double xbar = gram.get(icptCol, i);
        double x = (beta_given != null && proxPen != null)
          ? (y - ybar * gram.get(icptCol, i) + proxPen[i] * beta_given[i]) / ((gram.get(i, i) - xbar * xbar) + l2pen + proxPen[i])
          : ((y - ybar * xbar) / (gram.get(i, i) - xbar * xbar) + l2pen);///gram.get(i,i);
        rhos[i] = ADMM.L1Solver.estimateRho(x,l1pen, lb == null?Double.NEGATIVE_INFINITY:lb[i], ub == null?Double.POSITIVE_INFINITY:ub[i]);
      }
      // do the intercept separate as l1pen does not apply to it
      if (intercept && (lb != null && !Double.isInfinite(lb[icptCol]) || ub != null && !Double.isInfinite(ub[icptCol]))) {
        int icpt = xy.length - 1;
        rhos[icpt] = 1;//(xy[icpt] >= 0 ? xy[icpt] : -xy[icpt]);
      }
      if(!intercept) {
        gram.dropIntercept();
        xy = Arrays.copyOf(xy,xy.length-1);
      }
      if(l2pen > 0)
        gram.addDiag(l2pen);
      if(proxPen != null && beta_given != null) {
        gram.addDiag(proxPen);
        xy = xy.clone();
        for(int i = 0; i < xy.length; ++i)
          xy[i] += proxPen[i]*beta_given[i];
      }
      computeCholesky(gram,rhos,1e-5);
      _rho = rhos;
      _xy = xy;
    }

    private void computeCholesky(Gram gram, double [] rhos, double rhoAdd) {
      gram.addDiag(rhos);
      _chol = gram.cholesky(null,true,null);
      if(!_chol.isSPD())  { // make sure rho is big enough
        gram.addDiag(ArrayUtils.mult(rhos, -1));
        ArrayUtils.mult(rhos, -1);
        for(int i = 0; i < rhos.length; ++i)
          rhos[i] += rhoAdd;//1e-5;
        Log.info("Got NonSPD matrix with original rho, re-computing with rho = " + rhos[0]);
        _gram.addDiag(rhos);
        _chol = gram.cholesky(null,true,null);
        int cnt = 0;
        while(!_chol.isSPD() && cnt++ < 5) {
          gram.addDiag(ArrayUtils.mult(rhos, -1));
          ArrayUtils.mult(rhos, -1);
          for(int i = 0; i < rhos.length; ++i)
            rhos[i] *= 100;
          Log.warn("Still NonSPD matrix, re-computing with rho = " + rhos[0]);
            _gram.addDiag(rhos);
          _chol = gram.cholesky(null,true,null);
        }
        if(!_chol.isSPD())
          throw new NonSPDMatrixException();
      }
      gram.addDiag(ArrayUtils.mult(rhos, -1));
      ArrayUtils.mult(rhos, -1);
    }

    @Override
    public double [] rho() { return _rho;}

    @Override
    public boolean solve(double [] beta_given, double [] result) {
      if(beta_given != null)
        for(int i = 0; i < _xy.length; ++i)
          result[i] = _xy[i] + _rho[i] * beta_given[i];
      else
        System.arraycopy(_xy,0,result,0,_xy.length);
      _chol.solve(result);
      return true;
    }

    @Override
    public boolean hasGradient() { return false;}

    @Override
    public double[] gradient(double [] beta) {
      double [] grad = _gram.mul(beta);
      for(int i = 0; i < _xy.length; ++i)
        grad[i] -= _xy[i];
      return grad;
    }

    @Override
    public int iter() {
      return 0;
    }
  }


  public static class ProximalGradientInfo extends L_BFGS.GradientInfo {
    final GradientInfo _origGinfo;
    public ProximalGradientInfo(GradientInfo origGinfo, double objVal, double[] gradient) {
      super(objVal,gradient);
      _origGinfo = origGinfo;
    }
  }

  /**
   * Simple wrapper around gradient computation, adding proximal penalty
   */
  public static class ProximalGradientSolver extends L_BFGS.GradientSolver {
    final L_BFGS.GradientSolver _solver;
    final double [] _betaGiven;
    final double [] _rho;

    public ProximalGradientSolver(GradientSolver s, double [] betaGiven, double [] rho) {
      super();
      _solver = s;
      _betaGiven = betaGiven;
      _rho = rho;
    }

    @Override
    public GradientInfo getGradient(double[] beta) {
      GradientInfo gt = _solver.getGradient(beta);
      double [] grad = gt._gradient.clone();
      double obj = gt._objVal;
      for (int i = 0; i < gt._gradient.length; ++i) {
        double diff = (beta[i] - _betaGiven[i]);
        double pen = _rho[i] * diff;
        grad[i] += pen;
        obj += .5*pen*diff;
      }
      return new ProximalGradientInfo(gt,obj,grad);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] pk, int nSteps, double stepDec) {
      double [] objs = _solver.getObjVals(beta,pk, nSteps, stepDec);
      double step = 1;
      assert objs.length == nSteps;
      for (int i = 0; i < objs.length; ++i, step *= stepDec) {
        double [] b = ArrayUtils.wadd(beta.clone(), pk, step);
        double pen = 0;
        for (int j = 0; j < _betaGiven.length; ++j) {
          double diff = b[j] - _betaGiven[j];
          pen +=  _rho[j] * diff * diff;
        }
        objs[i] += .5 * pen;
      }
      return objs;
    }
  }

  public static final class GLMGradientInfo extends L_BFGS.GradientInfo {
    final double _likelihood;
    public GLMGradientInfo(double likelihood, double objVal, double[] grad) {
      super(objVal, grad);
      _likelihood = likelihood;
    }
  }
  
  public static final class LBFGS_ProximalSolver implements ProximalSolver {
    double [] _beta;
    final double [] _rho;
    final GradientSolver _gSolver;
    double [] _gradient;
    public int _iter;
    L_BFGS.ProgressMonitor _pm;
    double _gradEps = 1e-8;
    double _objEps = 1e-5;

    public LBFGS_ProximalSolver(GradientSolver gs, double [] beta, double [] rho, L_BFGS.ProgressMonitor pm){
      _gSolver = gs;
      _beta = beta;
      _rho = rho;
      _pm = pm;
    }
    public LBFGS_ProximalSolver setGradEps(double eps) {
      _gradEps = eps;
      return this;
    }

    public LBFGS_ProximalSolver setObjEps(double eps){
      _objEps = eps;
      return this;
    }
    
    @Override
    public double[] rho() { return _rho;}

    double [] _beta_given;
    GradientInfo _ginfo;
    @Override
    public boolean solve(double[] beta_given, double[] result) {
      ProximalGradientSolver s = new ProximalGradientSolver(_gSolver,beta_given,_rho);
      if(_beta_given == null)
        _beta_given = MemoryManager.malloc8d(beta_given.length);
      if(_ginfo != null) { // update the gradient
        for(int i = 0; i < beta_given.length; ++i) {
          _ginfo._gradient[i] += _rho[i] * (_beta_given[i] - beta_given[i]);
          _ginfo._objVal += .5 * _rho[i] *  (((_beta[i] - beta_given[i]) * (_beta[i] - beta_given[i])) -( (_beta[i] - _beta_given[i]) * (_beta[i] - _beta_given[i])));
          _beta_given[i] = beta_given[i];
        }
      } else _ginfo = s.getGradient(_beta);
      L_BFGS.Result r  = new L_BFGS().setMinIter(5).setObjEps(_objEps).setGradEps(_gradEps).solve(s, _beta, _ginfo, _pm);
      _ginfo = r.ginfo;
      _beta = r.coefs;
      _gradient = r.ginfo._gradient;
      _iter += r.iter;
      System.arraycopy(_beta,0,result,0,_beta.length);
      return r.converged;
    }
    @Override
    public boolean hasGradient() {
      return _gradient != null;
    }
    @Override
    public double[] gradient(double[] beta) {
      return _gSolver.getGradient(beta)._gradient;
    }
    public int iter() {
      return _iter;
    }
  }


  /**
   * Gradient and line search computation for L_BFGS and also L_BFGS solver wrapper (for ADMM)
   */
  public static final class GLMGradientSolver extends GradientSolver  {
    final GLMParameters _glmp;
    final DataInfo _dinfo;
    final double _ymu;
    final double _lambda;
    final double _reg;
    Vec _rowFilter;
    double [] _beta;

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, double wsum) {
      this(glmp, dinfo, lambda, ymu, wsum,  null);
    }

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, double wsum, Vec rowFilter) {
      _glmp = glmp;
      _dinfo = dinfo;
      _ymu = ymu;
//      _nobs = nobs;
      _lambda = lambda;
      _rowFilter = rowFilter;
      _reg = 1.0/wsum;
    }

    public GLMGradientSolver setBetaStart(double [] beta) {
      _beta = beta.clone();
      return this;
    }

    @Override
    public GLMGradientInfo getGradient(double[] beta) {
      assert beta.length == _dinfo.fullN()+1;
      if(!_glmp._intercept) // make sure intercept is 0
        beta[beta.length-1] = 0;
      GLMGradientTask gt = _glmp._family == Family.binomial
        ? new LBFGS_LogisticGradientTask(_dinfo, _glmp, _lambda, beta, _reg, _rowFilter ).doAll(_dinfo._adaptedFrame)
        :
      /*GLMGradientTask gt = */new GLMGradientTask(_dinfo, _glmp, _lambda, beta, _reg, _rowFilter).doAll(_dinfo._adaptedFrame);
      if(!_glmp._intercept) // no intercept, null the gradient
        gt._gradient[gt._gradient.length-1] = 0;
      return new GLMGradientInfo(gt._likelihood, gt._likelihood * _reg +  .5 * _lambda * ArrayUtils.l2norm2(beta,_dinfo._intercept), gt._gradient);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] direction, int nSteps, double stepDec) {
      double[] objs = new GLMLineSearchTask(_dinfo, _glmp, 1.0, beta, direction, stepDec, nSteps, _rowFilter).setFasterMetrics(true).doAll(_dinfo._adaptedFrame)._likelihoods;
      double step = 1;
      for (int i = 0; i < objs.length; ++i, step *= stepDec) {
        objs[i] *= _reg;
        if (_lambda > 0 ) { // have some l2 pen
          double[] b = ArrayUtils.wadd(beta.clone(), direction, step);
          if (_lambda > 0)
            objs[i] += .5 * _lambda * ArrayUtils.l2norm2(b, _dinfo._intercept);
        }
      }
      return objs;
    }
  }

  private static final double[] expandVec(double[] beta, final int[] activeCols, int fullN) {
    assert beta != null;
    if (activeCols == null) return beta;
    double[] res = MemoryManager.malloc8d(fullN);
    int i = 0;
    for (int c : activeCols)
      res[c] = beta[i++];
    res[res.length - 1] = beta[beta.length - 1];
    return res;
  }

  private final static double[] contractVec(double[] beta, final int[] activeCols) {
    if (beta == null) return null;
    if (activeCols == null) return beta.clone();
    double[] res = MemoryManager.malloc8d(activeCols.length + 1);
    int i = 0;
    for (int c : activeCols)
      res[i++] = beta[c];
    res[res.length - 1] = beta[beta.length - 1];
    return res;
  }
  private final static double[] resizeVec(double[] beta, final int[] activeCols, final int[] oldActiveCols, int fullN) {
    if (beta == null || Arrays.equals(activeCols, oldActiveCols)) return beta;
    double[] full = expandVec(beta, oldActiveCols, fullN);
    if (activeCols == null) return full;
    return contractVec(full, activeCols);
  }

}
