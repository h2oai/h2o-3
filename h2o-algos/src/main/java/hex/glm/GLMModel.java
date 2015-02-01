package hex.glm;

import hex.*;
import hex.FrameTask.DataInfo;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.schemas.GLMModelV2;
import water.*;
import water.DTask.DKeyTask;
import water.H2O.H2OCountedCompleter;
import water.api.ModelSchema;
import water.fvec.Chunk;
import water.util.ModelUtils;
import water.util.TwoDimTable;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by tomasnykodym on 8/27/14.
 * TODO: should be a subclass of SupervisedModel.
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
      _res._output.setSubmodelIdx(0);
    }
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    switch(_output.getModelCategory()) {
      case Binomial: return new ModelMetricsBinomial.MetricBuilderBinomial(domain, ModelUtils.DEFAULT_THRESHOLDS);
      case Regression: return new ModelMetricsRegression.MetricBuilderRegression();
      default: throw H2O.unimpl();
    }
  }

  @Override
  public ModelSchema schema() {
    return new GLMModelV2();
  }

  public double [] beta(){ return _output._global_beta;}

  public GLMValidation validation(){
    return _output._submodels[_output._best_lambda_idx].validation;
  }

  @Override
  public float[] score0(Chunk[] chks, int row_in_chunk, double[] tmp, float[] preds) {
    double eta = 0.0;
    final double [] b = beta();
    if(!_parms._use_all_factor_levels){ // skip level 0 of all factors
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i) if(chks[i].atd(row_in_chunk) != 0)
        eta += b[_dinfo._catOffsets[i] + (int)(chks[i].atd(row_in_chunk)-1)];
    } else { // do not skip any levels!
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i)
        eta += b[_dinfo._catOffsets[i] + (int)chks[i].atd(row_in_chunk)];
    }
    final int noff = _dinfo.numStart() - _dinfo._cats;
    for(int i = _dinfo._cats; i < b.length-1-noff; ++i)
      eta += b[noff+i]*chks[i].atd(row_in_chunk);
    eta += b[b.length-1]; // add intercept
    double mu = _parms.linkInv(eta);
    preds[0] = (float)mu;
    if( _parms._family == Family.binomial ) { // threshold for prediction
      if(Double.isNaN(mu)){
        preds[0] = Float.NaN;
        preds[1] = Float.NaN;
        preds[2] = Float.NaN;
      } else {
        preds[0] = (mu >= _output._threshold ? 1 : 0);
        preds[1] = 1.0f - (float)mu; // class 0
        preds[2] =        (float)mu; // class 1
      }
    }
    return preds;
  }

  @Override
  protected float[] score0(double[] data, float[] preds) {
    double eta = 0.0;
    final double [] b = beta();
    if(!_parms._use_all_factor_levels){ // skip level 0 of all factors
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i) if(data[i] != 0)
        eta += b[_dinfo._catOffsets[i] + (int)(data[i]-1)];
    } else { // do not skip any levels!
      for(int i = 0; i < _dinfo._catOffsets.length-1; ++i)
        eta += b[_dinfo._catOffsets[i] + (int)data[i]];
    }
    final int noff = _dinfo.numStart() - _dinfo._cats;
    for(int i = _dinfo._cats; i < data.length; ++i)
      eta += b[noff+i]*data[i];
    eta += b[b.length-1]; // add intercept
    double mu = _parms.linkInv(eta);
    preds[0] = (float)mu;
    if( _parms._family == Family.binomial ) { // threshold for prediction
      if(Double.isNaN(mu)){
        preds[0] = Float.NaN;
        preds[1] = Float.NaN;
        preds[2] = Float.NaN;
      } else {
        preds[0] = (mu >= _output._threshold ? 1 : 0);
        preds[1] = 1.0f - (float)mu; // class 0
        preds[2] =        (float)mu; // class 1
      }
    }
    return preds;
  }

  public static class GLMParameters extends SupervisedModel.SupervisedParameters {
    // public int _response; // TODO: the standard is now _response_column in SupervisedModel.SupervisedParameters
    public boolean _standardize = true;
    public final Family _family;
    public Link _link;
    public Solver _solver = Solver.ADMM;
    public final double _tweedie_variance_power;
    public final double _tweedie_link_power;
    public double [] _alpha;
    public double [] _lambda;
    public double _prior = -1;
    public boolean _lambda_search = false;
    public int _nlambdas = -1;
    public double _lambda_min_ratio = -1; // special
    public boolean _higher_accuracy = false;
    public boolean _use_all_factor_levels = false;
    public int _n_folds;
    // internal parameter, handle with care. GLM will stop when there is more than this number of active predictors (after strong rule screening)
    public int _max_active_predictors = 10000; // NOTE: Not brought out to the REST API

    public void validate(GLM glm) {
      if (_solver == Solver.L_BFGS) {
        glm.hide("_alpha", "L1 penalty is currently only available for ADMM solver.");
        glm.hide("_higher_accuracy","only available for ADMM");
        _alpha = new double[]{0};
      }
      if(!_lambda_search) {
        glm.hide("_lambda_min_ratio", "only applies if lambda search is on.");
        glm.hide("_nlambdas", "only applies if lambda search is on.");
      }
    }

    public GLMParameters(){
      this(Family.gaussian, Link.family_default);
      assert _link == Link.family_default;
    }
    public GLMParameters(Family f){this(f,f.defaultLink);}
    public GLMParameters(Family f, Link l){this(f,l,new double[]{1e-5},new double[]{.5});}
    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha){
      this._family = f;
      this._lambda = lambda;
      this._alpha = alpha;
      _tweedie_link_power = Double.NaN;
      _tweedie_variance_power = Double.NaN;
      if( f==Family.binomial ) _convert_to_enum = true;
      _link = l;
      // TODO: move these checks into GLM.init(boolean) so the front end gets proper validation_messages
      if(_link != Link.family_default) { // check we have compatible link
        // TODO: refactor these checks into sanityCheckParameters():
        this._link = l;
        switch (_family) {
          case gaussian:
            if (_link != Link.identity && _link != Link.log && _link != Link.inverse)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only identity, log and inverse links are allowed for family=gaussian.");
            break;
          case binomial:
            if (_link != Link.logit && _link != Link.log)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only logit and log links are allowed for family=binomial.");
            break;
          case poisson:
            if (_link != Link.log && _link != Link.identity)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only log and identity links are allowed for family=poisson.");
            break;
          case gamma:
            if (_link != Link.inverse && _link != Link.log && _link != Link.identity)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only inverse, log and identity links are allowed for family=gamma.");
            break;
          case tweedie:
            if (_link != Link.tweedie)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only tweedie link allowed for family=tweedie.");
            break;
          default:
            H2O.fail();
        }
      }
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
        case tweedie:
          return Math.pow(mu, _tweedie_variance_power);
        default:
          throw new RuntimeException("unknown family Id " + this);
      }
    }

    public double [] nullModelBeta(FrameTask.DataInfo dinfo, double ymu){
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
          return false; //return link == Link.inverse;
        case tweedie:
          return false;
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
        case tweedie:
          return y + (y==0?0.1:0);
        default:
          throw new RuntimeException("unimplemented");
      }
    }

    public final double deviance(double yr, double ym){
      switch(_family){
        case gaussian:
          return (yr - ym) * (yr - ym);
        case binomial:
          return 2 * ((y_log_y(yr, ym)) + y_log_y(1 - yr, 1 - ym));
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          // Theory of Dispersion Models: Jorgensen
          // pg49: $$ d(y;\mu) = 2 [ y \cdot \left(\tau^{-1}(y) - \tau^{-1}(\mu) \right) - \kappa \{ \tau^{-1}(y)\} + \kappa \{ \tau^{-1}(\mu)\} ] $$
          // pg133: $$ \frac{ y^{2 - p} }{ (1 - p) (2-p) }  - \frac{y \cdot \mu^{1-p}}{ 1-p} + \frac{ \mu^{2-p} }{ 2 - p }$$
          double one_minus_p = 1 - _tweedie_variance_power;
          double two_minus_p = 2 - _tweedie_variance_power;
          return Math.pow(yr, two_minus_p) / (one_minus_p * two_minus_p) - (yr * (Math.pow(ym, one_minus_p)))/one_minus_p + Math.pow(ym, two_minus_p)/two_minus_p;
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
        case tweedie:
          return Math.pow(x, _tweedie_link_power);
        default:
          throw new RuntimeException("unknown link function " + this);
      }
    }

    public final double linkDeriv(double x) {
      switch(_link) {
        case logit:
          return 1 / (x * (1 - x));
        case identity:
          return 1;
        case log:
          return 1.0 / x;
        case inverse:
          return -1.0 / (x * x);
        case tweedie:
          return _tweedie_link_power * Math.pow(x, _tweedie_link_power - 1);
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
        case tweedie:
          return Math.pow(x, 1/ _tweedie_link_power);
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
        case tweedie:
          double vp = (1. - _tweedie_link_power) / _tweedie_link_power;
          return (1/ _tweedie_link_power) * Math.pow(x, vp);
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }

    // supported families
    public enum Family {
      gaussian(Link.identity), binomial(Link.logit), poisson(Link.log),
      gamma(Link.inverse), tweedie(Link.tweedie);
      public final Link defaultLink;
      Family(Link link){defaultLink = link;}
    }
    public static enum Link {family_default, identity, logit, log,inverse,tweedie}

    public static enum Solver {ADMM, L_BFGS}

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
    GLMValidation validation;
    GLMValidation xvalidation;
    final int rank;
    final int [] idxs;
    final boolean sparseCoef;
    double []  beta;
    double []  norm_beta;

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
  public static void setSubmodel(H2O.H2OCountedCompleter cmp, Key modelKey, final double lambda, double[] beta, double[] norm_beta, final int iteration, long runtime, boolean sparseCoef, final GLMValidation val){
    final Submodel sm = new Submodel(lambda,beta, norm_beta, runtime, iteration,sparseCoef);
    sm.validation = val;
    cmp.addToPendingCount(1);
    new TAtomic<GLMModel>(cmp){
      @Override
      public GLMModel atomic(GLMModel old) {
        if(old == null)return old; // job could've been cancelled!
        if(old._output._submodels == null){
          old._output._submodels = new Submodel[]{sm};
        } else {
          int id = old._output.submodelIdForLambda(lambda);
          if (id < 0) {
            id = -id - 1;
            old._output._submodels = Arrays.copyOf(old._output._submodels, old._output._submodels.length + 1);
            for (int i = old._output._submodels.length - 1; i > id; --i)
              old._output._submodels[i] = old._output._submodels[i - 1];
          } else if (old._output._submodels[id].iteration > sm.iteration)
            return old;
          else
            old._output._submodels = old._output._submodels.clone();
          old._output._submodels[id] = sm;
          old._run_time = Math.max(old._run_time,sm.run_time);
        }
        old._output.pickBestModel(false);
        return old;
      }
    }.fork(modelKey);
  }

  public int rank(double lambda){return -1;}
  
  final double _lambda_max;
  final double _ymu;
  final long   _nobs;
  long   _run_time;
  
  public static class GLMOutput extends SupervisedModel.SupervisedOutput {
    Submodel [] _submodels;
    int         _best_lambda_idx;
    float       _threshold;
    double   [] _global_beta;
    String   [] _coefficient_names;
    TwoDimTable _coefficients_table;
    boolean _binomial;
    public int rank() {return rank(_submodels[_best_lambda_idx].lambda_value);}

    public GLMOutput() { }
    public GLMOutput(SupervisedModelBuilder b, DataInfo dinfo, boolean binomial){
      super(b);
      String [] cnames = dinfo.coefNames();
      String [] pnames = dinfo._adaptedFrame.names();
      String [] colTypes = new String[cnames.length+1];
      String [] colFormat = new String[cnames.length+1];
      Arrays.fill(colTypes, "double");
      Arrays.fill(colFormat, "%5f");
      _coefficient_names = Arrays.copyOf(cnames,cnames.length+1);
      _coefficient_names[cnames.length] = "Intercept";
      _coefficients_table = new TwoDimTable(
              "Best Lambda",
              new String []{"Coefficients", "Norm Coefficients"},
              _coefficient_names,
              colTypes,
              colFormat
      );
      _binomial = binomial;
    }

    @Override
    public int nclasses() {
      return _binomial?2:1;
    }
    private static String [] binomialClassNames = new String[]{"0","1"};
    @Override public String [] classNames(){
      return _binomial?binomialClassNames:null;
    }
    void addNullSubmodel(double lmax,double icept, GLMValidation val){
      assert _submodels == null;
      double [] beta = MemoryManager.malloc8d(_coefficient_names.length);
      beta[beta.length-1] = icept;
      _submodels = new Submodel[]{new Submodel(lmax,beta,beta,0,0,_coefficient_names.length > 750)};
      _submodels[0].validation = val;
    }
    public int  submodelIdForLambda(double lambda){
      if(lambda >= _submodels[0].lambda_value) return 0;
      int i = _submodels.length-1;
      for(;i >=0; --i)
        // first condition to cover lambda == 0 case (0/0 is Inf in java!)
        if(lambda == _submodels[i].lambda_value || Math.abs(_submodels[i].lambda_value - lambda)/lambda < 1e-5)
          return i;
        else if(_submodels[i].lambda_value > lambda)
          return -i-2;
      return -1;
    }
    public Submodel  submodelForLambda(double lambda){
      return _submodels[submodelIdForLambda(lambda)];
    }
    public int rank(double lambda) {
      Submodel sm = submodelForLambda(lambda);
      if(sm == null)return 0;
      return submodelForLambda(lambda).rank;
    }
    public void pickBestModel(boolean useAuc){
      int bestId = _submodels.length-1;
      if(_submodels.length > 2) {
        boolean xval = false;
        GLMValidation bestVal = null;
        for(Submodel sm:_submodels) {
          if(sm.xvalidation != null) {
            xval = true;
            bestVal = sm.xvalidation;
          }
        }
        if(!xval) bestVal = _submodels[0].validation;
        for (int i = 1; i < _submodels.length; ++i) {
          GLMValidation val = xval ? _submodels[i].xvalidation : _submodels[i].validation;
          if (val == null || val == bestVal) continue;
          if ((useAuc && val.auc > bestVal.auc) || val.residual_deviance < bestVal.residual_deviance) {
            bestVal = val;
            bestId = i;
          }
        }
      }
      setSubmodelIdx(_best_lambda_idx = bestId);
    }
    public void setSubmodelIdx(int l){
      _best_lambda_idx = l;
      _threshold = _submodels[l].validation == null?0.5f:_submodels[l].validation.best_threshold;
      if(_global_beta == null) _global_beta = MemoryManager.malloc8d(this._coefficient_names.length);
      else Arrays.fill(_global_beta,0);
      int j = 0;
      for(int i:_submodels[l].idxs) {
        _global_beta[i] = _submodels[l].beta[j];
        _coefficients_table.set(0, i, _submodels[l].beta[j]);
        _coefficients_table.set(1, i, _submodels[l].beta[j++]);
      }
    }
  }
  public static void setXvalidation(H2OCountedCompleter cmp, Key modelKey, final double lambda, final GLMValidation val){
    // expected cmp has already set correct pending count
    new TAtomic<GLMModel>(cmp){
      @Override
      public GLMModel atomic(GLMModel old) {
        if(old == null)return old; // job could've been cancelled
        old._output._submodels = old._output._submodels.clone();
        int id = old._output.submodelIdForLambda(lambda);
        old._output._submodels[id] = (Submodel)old._output._submodels[id].clone();
        old._output._submodels[id].xvalidation = val;
        old._output.pickBestModel(false);
        return old;
      }
    }.fork(modelKey);
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
    public FinalizeAndUnlockTsk(H2OCountedCompleter cmp, Key modelKey, Key jobKey){
      super(cmp, modelKey);
      _jobKey = jobKey;
    }
    @Override
    protected void map(GLMModel glmModel) {
      glmModel._output.pickBestModel(false);
      glmModel.update(_jobKey);
      glmModel.unlock(_jobKey);
    }
  }
}
