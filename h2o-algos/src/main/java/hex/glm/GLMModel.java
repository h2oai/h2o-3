package hex.glm;

import hex.*;
import hex.glm.GLMModel.GLMParameters.Family;
import water.*;
import water.DTask.DKeyTask;
import water.H2O.H2OCountedCompleter;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.JCodeGen;
import water.util.SB;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Created by tomasnykodym on 8/27/14.
 */
public class GLMModel extends SupervisedModel<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  final DataInfo _dinfo;
  public GLMModel(Key selfKey, GLMParameters parms, GLMOutput output, DataInfo dinfo, double ymu, double lambda_max, long nobs) {
    super(selfKey, parms, output);
    _ymu = ymu;
    _lambda_max = lambda_max;
    _nobs = nobs;
    _dinfo = dinfo;
  }

  public DataInfo dinfo() { return _dinfo; }
  
  public static class GetScoringModelTask extends DTask.DKeyTask<GetScoringModelTask,GLMModel> {
    final double _lambda;
    public GLMModel _res;
    public GetScoringModelTask(H2OCountedCompleter cmp, Key modelKey, double lambda){
      super(cmp,modelKey);
      _lambda = lambda;
    }
    @Override
    public void map(GLMModel m) {
      _res = m.clone();
      _res._output = (GLMOutput)_res._output.clone();
      Submodel sm = Double.isNaN(_lambda)?_res._output._submodels[_res._output._best_lambda_idx]:_res._output.submodelForLambda(_lambda);
      assert sm != null : "GLM[" + m._key + "]: missing submodel for lambda " + _lambda;
      sm = (Submodel) sm.clone();
      _res._output._submodels = new Submodel[]{sm};
      _res._output.setSubmodelIdx(0, _res,null, null);
    }
  }
  private int rank(double [] ds) {
    int res = 0;
    for(double d:ds)
      if(d != 0) ++res;
    return res;
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new GLMValidation(domain,_ymu, _parms, rank(beta()), _output._threshold);
  }

  public double [] beta() { return _output._global_beta;}
  public String [] names(){ return _output._names;}

  public GLMValidation validation(){
    return _output._submodels[_output._best_lambda_idx].trainVal;
  }

  @Override
  public double[] score0(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
    double eta = 0.0;
    final double [] b = beta();
    if(!_parms._use_all_factor_levels){ // good level 0 of all factors
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i) if(chks[i].atd(row_in_chunk) != 0)
        eta += b[_dinfo._catOffsets[i] + (int)(chks[i].atd(row_in_chunk)-1)];
    } else { // do not good any levels!
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i)
        eta += b[_dinfo._catOffsets[i] + (int)chks[i].atd(row_in_chunk)];
    }
    final int noff = _dinfo.numStart() - _dinfo._cats;
    for(int i = _dinfo._cats; i < b.length-1-noff; ++i)
      eta += b[noff+i]*chks[i].atd(row_in_chunk);
    eta += b[b.length-1]; // reduce intercept
    double mu = _parms.linkInv(eta);
    preds[0] = mu;
    if( _parms._family == Family.binomial ) { // threshold for prediction
      if(Double.isNaN(mu)){
        preds[0] = Double.NaN;
        preds[1] = Double.NaN;
        preds[2] = Double.NaN;
      } else {
        preds[0] = (mu >= _output._threshold ? 1 : 0);
        preds[1] = 1.0 - mu; // class 0
        preds[2] =       mu; // class 1
      }
    }
    return preds;
  }

  public static class GLMParameters extends SupervisedModel.SupervisedParameters {
    // public int _response; // TODO: the standard is now _response_column in SupervisedModel.SupervisedParameters
    public boolean _standardize = true;
    public Family _family;
    public Link _link = Link.family_default;
    public Solver _solver = Solver.IRLSM;
    public final double _tweedie_variance_power;
    public final double _tweedie_link_power;
    public double [] _alpha = null;
    public double [] _lambda = null;
    public double _prior = -1;
    public boolean _lambda_search = false;
    public int _nlambdas = -1;
    public double _lambda_min_ratio = -1; // special
    public boolean _use_all_factor_levels = false;
    public double _beta_epsilon = 1e-4;
    public int _max_iterations = -1;
    public int _n_folds;

    public Key<Frame> _beta_constraints = null;
    // internal parameter, handle with care. GLM will stop when there is more than this number of active predictors (after strong rule screening)
    public int _max_active_predictors = -1;

    public void validate(GLM glm) {
      if(_n_folds < 0) glm.error("n_folds","must be >= 0");
      if(_n_folds == 1)_n_folds = 0; // 0 or 1 means no n_folds
      if(_lambda_search && _nlambdas == -1)
        _nlambdas = 100;
      if(_beta_constraints != null) {
        Frame f = _beta_constraints.get();
        if(f == null) glm.error("beta_constraints","Missing frame for beta constraints");
        Vec v = f.vec("names");
        if(v == null)glm.error("beta_constraints","Beta constraints parameter must have names column with valid coefficient names");
        // todo: check the coefficient names
        v = f.vec("upper_bounds");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","upper_bounds must be numeric if present");v = f.vec("upper_bounds");
        v = f.vec("lower_bounds");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","lower_bounds must be numeric if present");
        v = f.vec("beta_given");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","beta_given must be numeric if present");v = f.vec("upper_bounds");
        v = f.vec("beta_start");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","beta_start must be numeric if present");
      }
      if(_family == Family.binomial) {
        Frame frame = DKV.getGet(_train);
        if (frame != null) {
          Vec response = frame.vec(_response_column);
          if (response != null) {
            if (response.min() != 0 || response.max() != 1) {
              glm.error("_response_column", "Illegal response for family binomial, must be binary, got min = " + response.min() + ", max = " + response.max() + ")");
            }
          }
        }
      }
      if(!_lambda_search) {
        glm.hide("_lambda_min_ratio", "only applies if lambda search is on.");
        glm.hide("_nlambdas", "only applies if lambda search is on.");
      }
      if(_link != Link.family_default) { // check we have compatible link
        switch (_family) {
          case gaussian:
            if (_link != Link.identity && _link != Link.log && _link != Link.inverse)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only identity, log and inverse links are allowed for family=gaussian.");
            break;
          case binomial:
            if (_link != Link.logit && _link != Link.log)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only logit and log links are allowed for family=binomial. Got " + _link);
            break;
          case poisson:
            if (_link != Link.log && _link != Link.identity)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only log and identity links are allowed for family=poisson.");
            break;
          case gamma:
            if (_link != Link.inverse && _link != Link.log && _link != Link.identity)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only inverse, log and identity links are allowed for family=gamma.");
            break;
//          case tweedie:
//            if (_link != Link.tweedie)
//              throw new IllegalArgumentException("Incompatible link function for selected family. Only tweedie link allowed for family=tweedie.");
//            break;
          default:
            H2O.fail();
        }
      }
    }

    public GLMParameters(){
      this(Family.gaussian, Link.family_default);
      assert _link == Link.family_default;
    }
    public GLMParameters(Family f){this(f,f.defaultLink);}
    public GLMParameters(Family f, Link l){this(f,l,null, null);}
    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha){
      this._family = f;
      this._lambda = lambda;
      this._alpha = alpha;
      _tweedie_link_power = Double.NaN;
      _tweedie_variance_power = Double.NaN;
      _link = l;
    }
    public GLMParameters(Family f, double [] lambda, double [] alpha, double twVar, double twLnk){
      this._lambda = lambda;
      this._alpha = alpha;
      this._tweedie_variance_power = twVar;
      this._tweedie_link_power = twLnk;
      _family = f;
      _link = f.defaultLink;
    }

    public final double variance(double mu){
      switch(_family) {
        case gaussian:
          return 1;
        case binomial:
//        assert (0 <= mu && mu <= 1) : "mu out of bounds<0,1>:" + mu;
          return mu * (1 - mu);
        case poisson:
          return mu;
        case gamma:
          return mu * mu;
//        case tweedie:
//          return Math.pow(mu, _tweedie_variance_power);
        default:
          throw new RuntimeException("unknown family Id " + this);
      }
    }

    public double [] nullModelBeta(DataInfo dinfo, double ymu){
      double [] res = MemoryManager.malloc8d(dinfo.fullN() + 1);
      res[res.length-1] = link(ymu);
      return res;
    }

    public final boolean canonical(){
      switch(_family){
        case gaussian:
          return _link == Link.identity;
        case binomial:
          return _link == Link.logit;
        case poisson:
          return _link == Link.log;
        case gamma:
          return _link == Link.inverse;
//        case tweedie:
//          return false;
        default:
          throw H2O.unimpl();
      }
    }

    public final double mustart(double y, double ymu) {
      switch(_family) {
        case gaussian:
        case binomial:
        case poisson:
          return ymu;
        case gamma:
          return y;
//        case tweedie:
//          return y + (y==0?0.1:0);
        default:
          throw new RuntimeException("unimplemented");
      }
    }

    public final double deviance(double yr, double ym){
      switch(_family){
        case gaussian:
          return (yr - ym) * (yr - ym);
        case binomial:
//          if(yr == ym) return 0;
//          return 2*( -yr * eta - Math.log(1 - ym));
          return 2 * ((y_log_y(yr, ym)) + y_log_y(1 - yr, 1 - ym));
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
//        case tweedie:
//          // Theory of Dispersion Models: Jorgensen
//          // pg49: $$ d(y;\mu) = 2 [ y \cdot \left(\tau^{-1}(y) - \tau^{-1}(\mu) \right) - \kappa \{ \tau^{-1}(y)\} + \kappa \{ \tau^{-1}(\mu)\} ] $$
//          // pg133: $$ \frac{ y^{2 - p} }{ (1 - p) (2-p) }  - \frac{y \cdot \mu^{1-p}}{ 1-p} + \frac{ \mu^{2-p} }{ 2 - p }$$
//          double one_minus_p = 1 - _tweedie_variance_power;
//          double two_minus_p = 2 - _tweedie_variance_power;
//          return Math.pow(yr, two_minus_p) / (one_minus_p * two_minus_p) - (yr * (Math.pow(ym, one_minus_p)))/one_minus_p + Math.pow(ym, two_minus_p)/two_minus_p;
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }
    public final double deviance(float yr, float ym){
      switch(_family){
        case gaussian:
          return (yr - ym) * (yr - ym);
        case binomial:
//          if(yr == ym) return 0;
//          return 2*( -yr * eta - Math.log(1 - ym));
          return 2 * ((y_log_y(yr, ym)) + y_log_y(1 - yr, 1 - ym));
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
//        case tweedie:
//          // Theory of Dispersion Models: Jorgensen
//          // pg49: $$ d(y;\mu) = 2 [ y \cdot \left(\tau^{-1}(y) - \tau^{-1}(\mu) \right) - \kappa \{ \tau^{-1}(y)\} + \kappa \{ \tau^{-1}(\mu)\} ] $$
//          // pg133: $$ \frac{ y^{2 - p} }{ (1 - p) (2-p) }  - \frac{y \cdot \mu^{1-p}}{ 1-p} + \frac{ \mu^{2-p} }{ 2 - p }$$
//          double one_minus_p = 1 - _tweedie_variance_power;
//          double two_minus_p = 2 - _tweedie_variance_power;
//          return Math.pow(yr, two_minus_p) / (one_minus_p * two_minus_p) - (yr * (Math.pow(ym, one_minus_p)))/one_minus_p + Math.pow(ym, two_minus_p)/two_minus_p;
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }

    public final double likelihood(double yr, double eta, double ym){
      switch(_family){
        case gaussian:
          return .5 * (yr - ym) * (yr - ym);
        case binomial:
          if(yr == ym) return 0;
          return .5 * deviance(yr, ym);
//          double res = Math.log(1 + Math.exp((1 - 2*yr) * eta));
//          assert Math.abs(res - .5 * deviance(yr,eta,ym)) < 1e-8:res + " != " + .5*deviance(yr,eta,ym) +" yr = "  + yr + ", ym = " + ym + ", eta = " + eta;
//          return res;
//
//          double res = -yr * eta - Math.log(1 - ym);
//          return res;
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
//        case tweedie:
//          // Theory of Dispersion Models: Jorgensen
//          // pg49: $$ d(y;\mu) = 2 [ y \cdot \left(\tau^{-1}(y) - \tau^{-1}(\mu) \right) - \kappa \{ \tau^{-1}(y)\} + \kappa \{ \tau^{-1}(\mu)\} ] $$
//          // pg133: $$ \frac{ y^{2 - p} }{ (1 - p) (2-p) }  - \frac{y \cdot \mu^{1-p}}{ 1-p} + \frac{ \mu^{2-p} }{ 2 - p }$$
//          double one_minus_p = 1 - _tweedie_variance_power;
//          double two_minus_p = 2 - _tweedie_variance_power;
//          return Math.pow(yr, two_minus_p) / (one_minus_p * two_minus_p) - (yr * (Math.pow(ym, one_minus_p)))/one_minus_p + Math.pow(ym, two_minus_p)/two_minus_p;
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }


    public final double link(double x) {
      switch(_link) {
        case identity:
          return x;
        case logit:
          assert 0 <= x && x <= 1:"x out of bounds, expected <0,1> range, got " + x;
          return Math.log(x / (1 - x));
        case log:
          return Math.log(x);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return 1.0 / xx;
//        case tweedie:
//          return Math.pow(x, _tweedie_link_power);
        default:
          throw new RuntimeException("unknown link function " + this);
      }
    }

    public final double linkDeriv(double x) {
      switch(_link) {
        case logit:
          double div = (x * (1 - x));
          if(div < 1e-6) return 1e6; // avoid numerical instability
          return 1.0 / div;
        case identity:
          return 1;
        case log:
          return 1.0 / x;
        case inverse:
          return -1.0 / (x * x);
//        case tweedie:
//          return _tweedie_link_power * Math.pow(x, _tweedie_link_power - 1);
        default:
          throw H2O.unimpl();
      }
    }

    public final double linkInv(double x) {
      switch(_link) {
        case identity:
          return x;
        case logit:
          return 1.0 / (Math.exp(-x) + 1.0);
        case log:
          return Math.exp(x);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return 1.0 / xx;
//        case tweedie:
//          return Math.pow(x, 1/ _tweedie_link_power);
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }

    public final double linkInvDeriv(double x) {
      switch(_link) {
        case identity:
          return 1;
        case logit:
          double g = Math.exp(-x);
          double gg = (g + 1) * (g + 1);
          return g / gg;
        case log:
          //return (x == 0)?MAX_SQRT:1/x;
          return Math.max(Math.exp(x), Double.MIN_NORMAL);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return -1 / (xx * xx);
//        case tweedie:
//          double vp = (1. - _tweedie_link_power) / _tweedie_link_power;
//          return (1/ _tweedie_link_power) * Math.pow(x, vp);
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }

    // supported families
    public enum Family {
      gaussian(Link.identity), binomial(Link.logit), poisson(Link.log),
      gamma(Link.inverse)/*, tweedie(Link.tweedie)*/;
      public final Link defaultLink;
      Family(Link link){defaultLink = link;}
    }
    public static enum Link {family_default, identity, logit, log,inverse,/* tweedie*/}

    public static enum Solver {AUTO, IRLSM, L_BFGS, COORDINATE_DESCENT}

    // helper function
    static final double y_log_y(double y, double mu) {
      if(y == 0)return 0;
      if(mu < Double.MIN_NORMAL) mu = Double.MIN_NORMAL;
      return y * Math.log(y / mu);
    }

  }
  public static class GLM_LBFGS_Parameters extends GLMParameters {}

  public static class Submodel extends Iced {
    final double lambda_value;
    final int        iteration;
    final long       run_time;

    GLMValidation trainVal;   // training set validation
    GLMValidation holdOutVal; // hold-out set validation
    GLMValidation xVal;       // x-validation
    final int rank;
    public final int [] idxs;
    final boolean sparseCoef;
    public double []  beta;
    public double []  norm_beta;

    public Submodel(double lambda , double [] beta, double [] norm_beta, long run_time, int iteration, boolean sparseCoef){
      this.lambda_value = lambda;
      this.run_time = run_time;
      this.iteration = iteration;
      int r = 0;
      if(beta != null){
        final double [] b = norm_beta != null?norm_beta:beta;
        // grab the indeces of non-zero coefficients
        for(double d:beta)if(d != 0)++r;
        idxs = MemoryManager.malloc4(sparseCoef?r:beta.length);
        int j = 0;
        for(int i = 0; i < beta.length; ++i)
          if(!sparseCoef || beta[i] != 0)idxs[j++] = i;
        j = 0;
        this.beta = MemoryManager.malloc8d(idxs.length);
        for(int i:idxs)
          this.beta[j++] = beta[i];
        if(norm_beta != null){
          j = 0;
          this.norm_beta = MemoryManager.malloc8d(idxs.length);
          for(int i:idxs) this.norm_beta[j++] = norm_beta[i];
        }
      } else idxs = null;
      rank = r;
      this.sparseCoef = sparseCoef;
    }
  }
  public static void setSubmodel(H2O.H2OCountedCompleter cmp, Key modelKey, final double lambda, double[] beta, double[] norm_beta, final int iteration, long runtime, boolean sparseCoef, final GLMValidation trainVal, final GLMValidation holdOutval, final Frame tFrame, final Frame vFrame){
    final Submodel sm = new Submodel(lambda,beta, norm_beta, runtime, iteration,sparseCoef);
    sm.trainVal = trainVal;
    sm.holdOutVal = holdOutval;
    if(cmp != null)
      cmp.addToPendingCount(1);
    Future f = new TAtomic<GLMModel>(cmp){
      @Override
      public GLMModel atomic(GLMModel old) {
        if(old == null)
          return old; // job could've been cancelled!
        if(old._output._submodels == null){
          old._output = (GLMOutput)old._output.clone();
          old._output._submodels = new Submodel[]{sm};
        } else {
          int id = old._output.submodelIdForLambda(lambda);
          if (id < 0) {
            id = -id - 1;
            Submodel [] sms = Arrays.copyOf(old._output._submodels, old._output._submodels.length + 1);
            for (int i = sms.length-1; i > id; --i)
              sms[i] = sms[i - 1];
            sms[id] = sm;
            old._output = (GLMOutput)old._output.clone();
            old._output._submodels = sms;
          } else {
            if (old._output._submodels[id].iteration <= sm.iteration)
              old._output._submodels[id] = sm;
          }
        }
//        can not call pickBestSubmodel now, pickBestSubmodel will call makeModelMEtrics which in turns calls Frame.checksum
//        which will request RollupStats and taht will hit assertion error since we are in TAtomic and would thus be blocking on task with the same prioriry
//        should be ok to pickBestModel only at the very end, may need to revisit if showing incremental results...
//        old._output.pickBestModel(false, old, tFrame, vFrame);
        old._run_time = Math.max(old._run_time,sm.run_time);
        return old;
      }
    }.fork(modelKey);
    if(cmp == null && f != null) try {
      f.get();
    } catch (InterruptedException e) {
      e.printStackTrace();
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
  }

  public int rank(double lambda){return -1;}
  
  final double _lambda_max;
  final double _ymu;
  final long   _nobs;
  long   _run_time;
  
  public static class GLMOutput extends SupervisedModel.SupervisedOutput {
    Submodel[] _submodels;
    String[] _coefficient_names;
    int _best_lambda_idx;
    double _threshold;
    double[] _global_beta;
    public boolean _binomial;

    public int rank() {
      return _submodels[_best_lambda_idx].rank;
    }

    public boolean isNormalized() {
      return _submodels != null && _submodels[_best_lambda_idx].norm_beta != null;
    }

    public String[] coefficientNames() {
      return _coefficient_names;
    }

    public GLMOutput(String[] column_names, String[][] domains, String[] coefficient_names, double[] coefficients, float threshold, boolean binomial) {
      _names = column_names;
      _domains = domains;
      _global_beta = coefficients;
      _coefficient_names = coefficient_names;
      _submodels = new Submodel[]{new Submodel(-1, coefficients, null, -1, -1, false)};
      _threshold = threshold;
      _binomial = binomial;
    }

    public GLMOutput() { }

    public GLMOutput(GLM glm) {
      super(glm);
      String[] cnames = glm._dinfo.coefNames();
      _names = glm._dinfo._adaptedFrame.names();
      _coefficient_names = Arrays.copyOf(cnames, cnames.length + 1);
      _coefficient_names[cnames.length] = "Intercept";
      _binomial = glm._parms._family == Family.binomial;
    }

    @Override
    public int nclasses() {
      return _binomial ? 2 : 1;
    }

    private static String[] binomialClassNames = new String[]{"0", "1"};

    @Override
    public String[] classNames() {
      return _binomial ? binomialClassNames : null;
    }

    void addNullSubmodel(double lmax, double icept, GLMValidation val) {
      assert _submodels == null;
      double[] beta = MemoryManager.malloc8d(_names.length);
      beta[beta.length - 1] = icept;
      _submodels = new Submodel[]{new Submodel(lmax, beta, beta, 0, 0, _names.length > 750)};
      _submodels[0].trainVal = val;
    }

    public int submodelIdForLambda(double lambda) {
      if (lambda >= _submodels[0].lambda_value) return 0;
      int i = _submodels.length - 1;
      for (; i >= 0; --i)
        // first condition to cover lambda == 0 case (0/0 is Inf in java!)
        if (lambda == _submodels[i].lambda_value || Math.abs(_submodels[i].lambda_value - lambda) / lambda < 1e-5)
          return i;
        else if (_submodels[i].lambda_value > lambda)
          return -i - 2;
      return -1;
    }

    public Submodel submodelForLambda(double lambda) {
      return _submodels[submodelIdForLambda(lambda)];
    }

    public int rank(double lambda) {
      Submodel sm = submodelForLambda(lambda);
      if (sm == null) return 0;
      return submodelForLambda(lambda).rank;
    }

    public void pickBestModel(boolean useAuc, GLMModel m, Frame tFrame, Frame vFrame) {
      int bestId = _submodels.length - 1;
      if (_submodels.length > 2) {
        boolean xval = false;
        boolean hval = false;
        GLMValidation bestVal = null;
        if (_submodels[1].xVal != null) { // skip null model
          xval = true;
          bestVal = _submodels[1].xVal;
        } else if (_submodels[1].holdOutVal != null) {
          hval = true;
          bestVal = _submodels[1].holdOutVal;
        } else
          bestVal = _submodels[0].trainVal;
        for (int i = 1; i < _submodels.length; ++i) {
          GLMValidation val = xval ? _submodels[i].xVal : hval ? _submodels[i].holdOutVal : _submodels[i].trainVal;
          Frame f = hval ? vFrame : tFrame;
          if (val == null || val == bestVal) continue;
          if ((useAuc && val.computeAUC(m.clone(), f) > bestVal.computeAUC(m, f)) || val.residual_deviance < bestVal.residual_deviance) {
            bestVal = val;
            bestId = i;
          }
        }
      }
      setSubmodelIdx(_best_lambda_idx = bestId, m, tFrame, vFrame);
    }

    public ModelMetrics setModelMetrics(ModelMetrics mm) {
      for (int i = 0; i < _model_metrics.length; ++i) // Dup removal
        if (_model_metrics[i].equals(mm._key)) {
          _model_metrics[i] = mm._key;
          return mm;
        }
      _model_metrics = Arrays.copyOf(_model_metrics, _model_metrics.length + 1);
      _model_metrics[_model_metrics.length - 1] = mm._key;
      return mm;                // Flow coding
    }

    public void setSubmodelIdx(int l, GLMModel m, Frame tFrame, Frame vFrame){
      _best_lambda_idx = l;
      if (_submodels[l].trainVal != null && tFrame != null)
        _training_metrics = _submodels[l].trainVal.makeModelMetrics(m,tFrame,Double.NaN);
      if(_submodels[l].holdOutVal != null && vFrame != null) {
        _threshold = _submodels[l].trainVal.bestThreshold();
        _validation_metrics = _submodels[l].holdOutVal.makeModelMetrics(m,vFrame,Double.NaN);
      }
      if(_global_beta == null) _global_beta = MemoryManager.malloc8d(_coefficient_names.length);
      else Arrays.fill(_global_beta,0);
      int j = 0;
      for(int i:_submodels[l].idxs)
        _global_beta[i] = _submodels[l].beta[j++];
//      public TwoDimTable(String tableHeader, String tableDescription, String[] rowHeaders, String[] colHeaders, String[] colTypes,
//        String[] colFormats, String colHeaderForRowHeaders) {
//      _model_summary = new TwoDimTable("Model Summary","Summary", new String[]{"Degrees Of Freedom", "Deviance"});
    }

    public double [] beta() { return _global_beta;}
    public Submodel bestSubmodel(){ return _submodels[_best_lambda_idx];}
  }

  public static void setXvalidation(H2OCountedCompleter cmp, Key modelKey, final double lambda, final GLMValidation val){
    throw H2O.unimpl();
//    // expected cmp has already set correct pending count
//    new TAtomic<GLMModel>(cmp){
//      @Override
//      public GLMModel atomic(GLMModel old) {
//        if(old == null)return old; // job could've been cancelled
//        old._output._submodels = old._output._submodels.clone();
//        int id = old._output.submodelIdForLambda(lambda);
//        old._output._submodels[id] = (Submodel)old._output._submodels[id].clone();
//        old._output._submodels[id].xVal = val;
//        old._output.pickBestModel(false);
//
//        return old;
//      }
//    }.fork(modelKey);
  }
  /**
   * get beta coefficients in a map indexed by name
   * @return the estimated coefficients
   */
  public HashMap<String,Double> coefficients(){
    HashMap<String, Double> res = new HashMap<String, Double>();
    final double [] b = beta();
    if(b != null) for(int i = 0; i < b.length; ++i)res.put(_output._coefficient_names[i],b[i]);
    return res;
  }

  static class FinalizeAndUnlockTsk extends DKeyTask<FinalizeAndUnlockTsk,GLMModel> {
    final Key _jobKey;
    final Key _validFrame;
    final Key _trainFrame;
    final double [] _likelihoods;
    final double [] _objectives;
    final int [] _scoring_iters;

    public FinalizeAndUnlockTsk(H2OCountedCompleter cmp, Key modelKey, Key jobKey, Key trainFrame, Key validFrame, int [] scoring_iters, double [] likelihoods, double [] objectives){
      super(cmp, modelKey);
      _jobKey = jobKey;
      _validFrame = validFrame;
      _trainFrame = trainFrame;
      _scoring_iters = scoring_iters;
      _likelihoods = likelihoods;
      _objectives = objectives;
    }

    @Override
    protected void map(GLMModel glmModel) {
      Frame tFrame = DKV.getGet(_trainFrame);
      Frame vFrame = (_validFrame != null)?
        DKV.get(_validFrame).<Frame>get():null;
      glmModel._output.pickBestModel(glmModel._parms._family == Family.binomial, glmModel, tFrame, vFrame);
      //  String[] colTypes,
//      /String[] colFormats, String colHeaderForRowHeaders) {
      glmModel._output._model_summary = new TwoDimTable("GLM Model", "summary", new String[]{""}, new String[]{"Family","Link","Training Frame","Number of Predictors"}, new String[]{"string","string","string","int"},new String[]{"%s","%s","%s","%d"},"");
      glmModel._output._model_summary.set(0,0,glmModel._parms._family.toString());
      glmModel._output._model_summary.set(0,1,glmModel._parms._link.toString());
      glmModel._output._model_summary.set(0,2,_trainFrame.toString());
      glmModel._output._model_summary.set(0,3,Integer.toString(glmModel.beta().length));
      if(_scoring_iters != null) {
        glmModel._output._scoring_history = new TwoDimTable("Scoring History", "", new String[_scoring_iters.length], new String[]{"iteration", "likelihood", "objective"}, new String[]{"int", "double", "double"}, new String[]{"%d", "%.5f", "%.5f"}, "");
        for (int i = 0; i < _scoring_iters.length; ++i) {
          glmModel._output._scoring_history.set(i,0,_scoring_iters[i]);
          glmModel._output._scoring_history.set(i,1,_likelihoods[i]);
          glmModel._output._scoring_history.set(i,2,_objectives[i]);
        }
      }
      glmModel.update(_jobKey);
      glmModel.unlock(_jobKey);
    }
  }

  @Override protected double[] score0(double[] data, double[] preds) {
    double eta = 0.0;
    final double [] b = beta();
    if(!_parms._use_all_factor_levels){ // good level 0 of all factors
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i) if(data[i] != 0)
        eta += b[_dinfo._catOffsets[i] + (int)(data[i]-1)];
    } else { // do not good any levels!
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i)
        eta += b[_dinfo._catOffsets[i] + (int)data[i]];
    }
    final int noff = _dinfo.numStart() - _dinfo._cats;
    for(int i = _dinfo._cats; i < data.length; ++i)
      eta += b[noff+i]*data[i];
    eta += b[b.length-1]; // reduce intercept
    double mu = _parms.linkInv(eta);
    preds[0] = mu;
    if( _parms._family == Family.binomial ) { // threshold for prediction
      if(Double.isNaN(mu)){
        preds[0] = Double.NaN;
        preds[1] = Double.NaN;
        preds[2] = Double.NaN;
      } else {
        preds[0] = (mu >= _output._threshold ? 1 : 0);
        preds[1] = 1.0 - mu; // class 0
        preds[2] =       mu; // class 1
      }
    }
    return preds;
  }

  @Override protected void toJavaPredictBody(SB body, SB classCtx, SB file) {
    final int nclass = _output.nclasses();
    String mname = JCodeGen.toJavaId(_key.toString());
    JCodeGen.toStaticVar(classCtx,"BETA",beta(),"The Coefficients");
    JCodeGen.toStaticVar(classCtx,"CATOFFS",_dinfo._catOffsets,"Categorical Offsets");
    body.ip("double eta = 0.0;").nl();
    body.ip("final double [] b = BETA;").nl();
    if(!_parms._use_all_factor_levels){ // good level 0 of all factors
      body.ip("for(int i = 0; i < CATOFFS.length-1; ++i) if(data[i] != 0)").nl();
      body.ip("  eta += b[CATOFFS[i] + (int)(data[i]-1)];").nl();
    } else { // do not good any levels!
      body.ip("for(int i = 0; i < CATOFFS.length-1; ++i)").nl();
      body.ip("  eta += b[CATOFFS[i] + (int)(data[i])];").nl();
    }
    final int noff = _dinfo.numStart() - _dinfo._cats;
    body.ip("for(int i = ").p(_dinfo._cats).p("; i < data.length; ++i)").nl();
    body.ip("  eta += b[").p(noff).p("+i]*data[i];").nl();
    body.ip("eta += b[b.length-1]; // reduce intercept").nl();
    body.ip("double mu = hex.genmodel.GenModel.GLM_").p(_parms._link.toString()).p("Inv(eta");
//    if( _parms._link == hex.glm.GLMModel.GLMParameters.Link.tweedie ) body.p(",").p(_parms._tweedie_link_power);
    body.p(");").nl();
    body.ip("preds[0] = mu;").nl();
    if( _parms._family == Family.binomial ) { // threshold for prediction
      body.ip("preds[0] = mu > ").p(_output._threshold).p(" ? 1 : 0);").nl();
      body.ip("preds[1] = 1.0 - mu; // class 0").nl();
      body.ip("preds[2] =       mu; // class 1").nl();
    }
  }

  @Override protected SB toJavaInit(SB sb, SB fileContext) {
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_output.nclasses()+"; }").nl();
    sb.ip("public ModelCategory getModelCategory() { return ModelCategory."+_output.getModelCategory()+"; }").nl();
    return sb;
  }
}
