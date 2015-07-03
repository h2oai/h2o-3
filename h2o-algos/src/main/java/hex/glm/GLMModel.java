package hex.glm;

import hex.*;
import hex.DataInfo.TransformType;
import hex.glm.GLMModel.GLMParameters.Family;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by tomasnykodym on 8/27/14.
 */
public class GLMModel extends Model<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  public GLMModel(Key selfKey, GLMParameters parms, GLM job, double ymu, double ySigma, double lambda_max, long nobs, boolean hasWeights, boolean hasOffset) {
    super(selfKey, parms, null);

    // modelKey, parms, null, Double.NaN, Double.NaN, Double.NaN, -1
    _ymu = ymu;
    _ySigma = ySigma;
    _lambda_max = lambda_max;
    _nobs = nobs;
    _output = job == null?new GLMOutput():new GLMOutput(job);
  }

  @Override
  protected boolean toJavaCheckTooBig() {
    if(beta() != null && beta().length > 10000) {
      Log.warn("toJavaCheckTooBig must be overridden for this model type to render it in the browser");
      return true;
    }
    return false;
  }

  public DataInfo dinfo() { return _output._dinfo; }


  private int rank(double [] ds) {
    int res = 0;
    for(double d:ds)
      if(d != 0) ++res;
    return res;
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    if(domain == null && _parms._family == Family.binomial)
      domain = new String[]{"0","1"};
    return new GLMValidation(domain,_parms._intercept, _ymu, _parms, rank(beta()), _output._threshold, true);
  }

  public double [] beta() { return _output._global_beta;}
  public String [] names(){ return _output._names;}



  public static class GLMParameters extends Model.Parameters {
    // public int _response; // TODO: the standard is now _response_column in SupervisedModel.SupervisedParameters
    public boolean _standardize = true;
    public Family _family;
    public Link _link = Link.family_default;
    public Solver _solver = Solver.IRLSM;
    public double _tweedie_variance_power;
    public double _tweedie_link_power;
    public double [] _alpha = null;
    public double [] _lambda = null;
    public double _prior = -1;
    public boolean _lambda_search = false;
    public int _nlambdas = -1;
    public boolean _non_negative = false;
    public boolean _exactLambdas = false;
    public double _lambda_min_ratio = -1; // special
    public boolean _use_all_factor_levels = false;
    public int _max_iterations = -1;
    public int _n_folds;
    public boolean _intercept = true;
    public double _beta_epsilon = 1e-4;
    public double _objective_epsilon = 1e-5;
    public double _gradient_epsilon = 1e-4;

    public Key<Frame> _beta_constraints = null;
    // internal parameter, handle with care. GLM will stop when there is more than this number of active predictors (after strong rule screening)
    public int _max_active_predictors = -1;

    public void validate(GLM glm) {
      if(_n_folds < 0) glm.error("n_folds","must be >= 0");
      if(_n_folds == 1)_n_folds = 0; // 0 or 1 means no n_folds
      if(_weights_column != null && _offset_column != null && _weights_column.equals(_offset_column))
        glm.error("_offset_column", "Offset must be different from weights");
      if(_lambda_search)
        if(_nlambdas == -1)
          _nlambdas = 100;
        else
          _exactLambdas = false;
      if(_family != Family.tweedie) {
        glm.hide("tweedie_variance_power","Only applicable with Tweedie family");
        glm.hide("tweedie_link_power","Only applicable with Tweedie family");
      }

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
            if (_link != Link.logit) // fixme: R also allows log, but it's not clear when can be applied and what should we do in case the predictions are outside of 0/1.
              throw new IllegalArgumentException("Incompatible link function for selected family. Only logit is allowed for family=binomial. Got " + _link);
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

    public GLMParameters(){
      this(Family.gaussian, Link.family_default);
      assert _link == Link.family_default;
    }
    public GLMParameters(Family f){this(f,f.defaultLink);}
    public GLMParameters(Family f, Link l){this(f,l, null, null, 0, 1);}

    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha, double twVar, double twLnk){
      this._lambda = lambda;
      this._alpha = alpha;
      this._tweedie_variance_power = twVar;
      this._tweedie_link_power = twLnk;
      _family = f;
      _link = l;
    }

    public final double variance(double mu){
      switch(_family) {
        case gaussian:
          return 1;
        case binomial:
          return mu * (1 - mu);
        case poisson:
          return mu;
        case gamma:
          return mu * mu;
        case tweedie:
          return Math.pow(mu, _tweedie_variance_power);
        default:
          throw new RuntimeException("unknown family Id " + this._family);
      }
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

    public final double deviance(double yr, double ym){
      double y1 = yr == 0?.1:yr;
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
          double theta = _tweedie_variance_power == 1
            ?Math.log(y1/ym)
            :(Math.pow(y1,1.-_tweedie_variance_power) - Math.pow(ym,1 - _tweedie_variance_power))/(1-_tweedie_variance_power);
          double kappa = _tweedie_variance_power == 2
            ?Math.log(y1/ym)
            :(Math.pow(yr,2-_tweedie_variance_power) - Math.pow(ym,2-_tweedie_variance_power))/(2 - _tweedie_variance_power);
          return 2 * (yr * theta - kappa);
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }
    public final double deviance(float yr, float ym){
     return deviance((double)yr,(double)ym);
    }

    public final double likelihood(double yr, double ym){
      switch(_family){
        case gaussian:
          return .5 * (yr - ym) * (yr - ym);
        case binomial:
          if(yr == ym) return 0;
          return .5 * deviance(yr, ym);
//          double res = Math.log(1 + Math.exp((1 - 2*yr) * eta));
//          assert Math.abs(res - .5 * deviance(yr,eta,ym)) < 1e-8:res + " != " + .5*deviance(yr,eta,ym) +" yr = "  + yr + ", ym = " + ym + ", eta = " + eta;
//          return res;
//          double res = -yr * eta - Math.log(1 - ym);
//          return res;
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          return deviance(yr,ym); //fixme: not really correct, not sure what the likelihood is right now
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
          return _tweedie_link_power == 0?Math.log(x):Math.pow(x, _tweedie_link_power);
        default:
          throw new RuntimeException("unknown link function " + this);
      }
    }

    public final double linkDeriv(double x) { // note: compute an inverse of what R does
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
        case tweedie:
//          double res = _tweedie_link_power == 0
//            ?Math.max(2e-16,Math.exp(x))
//            // (1/lambda) * eta^(1/lambda - 1)
//            :(1.0/_tweedie_link_power) * Math.pow(link(x), 1.0/_tweedie_link_power - 1.0);

          return _tweedie_link_power == 0
            ?1.0/Math.max(2e-16,x)
            :_tweedie_link_power * Math.pow(x,_tweedie_link_power-1);
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
          return _tweedie_link_power == 0
            ?Math.max(2e-16,Math.exp(x))
            :Math.pow(x, 1/ _tweedie_link_power);
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
      gamma(Link.inverse), tweedie(Link.tweedie);
      public final Link defaultLink;
      Family(Link link){defaultLink = link;}
    }
    public static enum Link {family_default, identity, logit, log,inverse, tweedie}

    public static enum Solver {AUTO, IRLSM, L_BFGS /*, COORDINATE_DESCENT*/}

    // helper function
    static final double y_log_y(double y, double mu) {
      if(y == 0)return 0;
      if(mu < Double.MIN_NORMAL) mu = Double.MIN_NORMAL;
      return y * Math.log(y / mu);
    }
  }

  public static class Submodel extends Iced {
    public final double lambda_value;
    public final int    iteration;
    public final double devianceTrain;
    public final double devianceTest;
    public final int    [] idxs;
    public final double [] beta;

    public int rank(){
      return idxs != null?idxs.length+1:beta.length;
    }

    public Submodel(double lambda , double [] beta, int iteration, double devTrain, double devTest){
      this.lambda_value = lambda;
      this.iteration = iteration;
      this.devianceTrain = devTrain;
      this.devianceTest = devTest;
      int r = 0;
      if(beta != null){
        // grab the indeces of non-zero coefficients
        for(double d:beta)if(d != 0)++r;
        idxs = MemoryManager.malloc4(r);
        int j = 0;
        for(int i = 0; i < beta.length; ++i)
          if(beta[i] != 0)idxs[j++] = i;
        j = 0;
        this.beta = MemoryManager.malloc8d(idxs.length);
        for(int i:idxs)
          this.beta[j++] = beta[i];
      } else {
        this.beta = null;
        idxs = null;
      }
    }
  }

  public final double _lambda_max;
  public final double _ymu;
  public final double _ySigma;
  public final long   _nobs;

  public static class GLMOutput extends Model.Output {
    Submodel[] _submodels;
    DataInfo _dinfo;
    String[] _coefficient_names;
    public int _best_lambda_idx;

    double _threshold;
    double[] _global_beta;
    public boolean _binomial;

    public int rank() { return _submodels[_best_lambda_idx].rank();}

    public boolean isStandardized() {
      return _dinfo._predictor_transform == TransformType.STANDARDIZE;
    }

    public String[] coefficientNames() {
      return _coefficient_names;
    }

    public GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial) {
      super(dinfo._weights, dinfo._offset);
      _dinfo = dinfo;
      _names = column_names;
      _domains = domains;
      _coefficient_names = coefficient_names;
      _binomial = binomial;
      if(_binomial && domains[domains.length-1] != null) {
        assert domains.length == 2;
        binomialClassNames = domains[domains.length - 1];
      }
    }

    public GLMOutput() {_isSupervised = true;}

    public GLMOutput(GLM glm) {
      super(glm);
      _dinfo = glm._dinfo;
      String[] cnames = glm._dinfo.coefNames();
      _names = glm._dinfo._adaptedFrame.names();
      _domains = glm._dinfo._adaptedFrame.domains();
      _coefficient_names = Arrays.copyOf(cnames, cnames.length + 1);
      _coefficient_names[_coefficient_names.length-1] = "Intercept";
      _binomial = glm._parms._family == Family.binomial;
    }

    @Override
    public int nclasses() {
      return _binomial ? 2 : 1;
    }

    private String[] binomialClassNames = new String[]{"0", "1"};

    @Override
    public String[] classNames() {
      return _binomial ? binomialClassNames : null;
    }

    public void pickBestModel() {
      int i = _submodels.length - 1;
      while(i > 0 && _submodels[i-1].devianceTest <= _submodels[i].devianceTest)--i;
      setSubmodelIdx(_best_lambda_idx = i);
    }

    public double[] getNormBeta() {
      double [] res = MemoryManager.malloc8d(_dinfo.fullN()+1);
      getBeta(_best_lambda_idx,res);
      return res;
    }
    public void getBeta(int l, double [] beta) {
      assert beta.length == _dinfo.fullN()+1;
      int k = 0;
      for(int i:_submodels[l].idxs)
        beta[i] = _submodels[l].beta[k++];
    }
    public void setSubmodelIdx(int l){
      _best_lambda_idx = l;
      if(_global_beta == null) _global_beta = MemoryManager.malloc8d(_coefficient_names.length);
      else Arrays.fill(_global_beta,0);
      getBeta(l,_global_beta);
      _global_beta = _dinfo.denormalizeBeta(_global_beta);
    }
    public double [] beta() { return _global_beta;}
    public Submodel bestSubmodel(){ return _submodels[_best_lambda_idx];}
  }

  /**
   * get beta coefficients in a map indexed by name
   * @return the estimated coefficients
   */
  public HashMap<String,Double> coefficients(){
    HashMap<String, Double> res = new HashMap<>();
    final double [] b = beta();
    if(b != null) for(int i = 0; i < b.length; ++i)res.put(_output._coefficient_names[i],b[i]);
    return res;
  }


  public synchronized void setSubmodel(Submodel sm) {
    int i = 0;
    if(_output._submodels == null) {
      _output._submodels = new Submodel[]{sm};
      return;
    }
    for(; i < _output._submodels.length; ++i)
      if(_output._submodels[i].lambda_value <= sm.lambda_value)
        break;
    if(i == _output._submodels.length) {
      _output._submodels = Arrays.copyOf(_output._submodels,_output._submodels.length+1);
      _output._submodels[_output._submodels.length-1] = sm;
    } else if(_output._submodels[i].lambda_value > sm.lambda_value) {
      _output._submodels = Arrays.copyOf(_output._submodels, _output._submodels.length + 1);
      for (int j = _output._submodels.length - 1; j > i; --j)
        _output._submodels[j] = _output._submodels[j - 1];
      _output._submodels[i] = sm;
    } else  _output._submodels[i] = sm;
  }

  // TODO: Shouldn't this be in schema? have it here for now to be consistent with others...
  /**
   * Re-do the TwoDim table generation with updated model.
   */
  public TwoDimTable generateSummary(Key train, int iter){
    String[] names = new String[]{"Family", "Link", "Regularization", "Number of Predictors Total", "Number of Active Predictors", "Number of Iterations", "Training Frame"};
    String[] types = new String[]{"string", "string", "string", "int", "int", "int", "string"};
    String[] formats = new String[]{"%s", "%s", "%s", "%d", "%d", "%d", "%s"};
    if (_parms._lambda_search) {
      names = new String[]{"Family", "Link", "Regularization", "Lambda Search", "Number of Predictors Total", "Number of Active Predictors", "Number of Iterations", "Training Frame"};
      types = new String[]{"string", "string", "string", "string", "int", "int", "int", "string"};
      formats = new String[]{"%s", "%s", "%s", "%s", "%d", "%d", "%d", "%s"};
    }
    _output._model_summary = new TwoDimTable("GLM Model", "summary", new String[]{""}, names, types, formats, "");
    _output._model_summary.set(0, 0, _parms._family.toString());
    _output._model_summary.set(0, 1, _parms._link.toString());
    String regularization = "None";
    if (_parms._lambda != null && !(_parms._lambda.length == 1 && _parms._lambda[0] == 0)) { // have regularization
      if (_parms._alpha[0] == 0)
        regularization = "Ridge ( lambda = ";
      else if (_parms._alpha[0] == 1)
        regularization = "Lasso (lambda = ";
      else
        regularization = "Elastic Net (alpha = " + MathUtils.roundToNDigits(_parms._alpha[0], 4) + ", lambda = ";
      regularization = regularization + MathUtils.roundToNDigits(_parms._lambda[_output._best_lambda_idx], 4) + " )";
    }
    _output._model_summary.set(0, 2, regularization);
    int lambdaSearch = 0;
    if (_parms._lambda_search) {
      lambdaSearch = 1;
      _output._model_summary.set(0, 3, "nlambda = " + _parms._nlambdas + ", lambda_max = " + MathUtils.roundToNDigits(_lambda_max, 4) + ", best_lambda = " + MathUtils.roundToNDigits(_output.bestSubmodel().lambda_value, 4));
    }
    int intercept = _parms._intercept ? 1 : 0;
    _output._model_summary.set(0, 3 + lambdaSearch, Integer.toString(beta().length - intercept));
    _output._model_summary.set(0, 4 + lambdaSearch, Integer.toString(_output.rank() - intercept));
    _output._model_summary.set(0, 5 + lambdaSearch, Integer.valueOf(iter));
    _output._model_summary.set(0, 6 + lambdaSearch, train.toString());
    return _output._model_summary;
  }

  /**
   * Make GLM model with given coefficients (predictors can be numeric only at the moment)
   *
   * Example: @see GLMTest.testMakeModel().
   *
   * @param fam - glm family, always uses canonical link
   * @param coefficients - vector of coefficients, assumed the same order as predictor names, intercept in the end
   * @param predictors - NAmes of predictor columns, does not include Intercept
   * @return GLM model usable for scoring
   */
  public static GLMModel makeGLMModel(Family fam, double [] coefficients, String [] predictors, String response) {
    if(coefficients.length != predictors.length+1)
      throw new IllegalArgumentException("coefficients length is expected to be predictros.length + 1, as each coefficient must have name + intercept term with no name.");
    GLMParameters parms = new GLMParameters(Family.binomial);
    parms._alpha = new double[]{0};
    parms._lambda = new double[]{0};
    parms._standardize = false;
    parms._prior = -1;
    parms._train = null;
    GLMModel m = new GLMModel(Key.make(),parms,null, fam == Family.binomial?.5:0,Double.NaN,Double.NaN,-1, false, false);
    predictors = ArrayUtils.append(predictors, new String[]{response});
    m._output._names = predictors;
    m._output._coefficient_names = predictors;
    m._output._dinfo = DataInfo.makeEmpty(coefficients.length-1);
    m._output._domains = new String[predictors.length][];
    // double lambda , double [] beta, int iteration, double devTrain, double devTest
    m.setSubmodel(new Submodel(0, coefficients, -1, Double.NaN, Double.NaN));
    m._output.setSubmodelIdx(0);
    return m;
  }

  @Override public long checksum_impl(){
    if(_parms._train == null) return 0;
    return super.checksum_impl();
  }

  @Override
  public double[] score0(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {

    /*

     public final double[] score0( double[] data, double[] preds ) {
    double eta = 0.0;
    final double [] b = BETA;
    for(int i = 0; i < CATOFFS.length-1; ++i) if(data[i] != 0) {
      int ival = (int)data[i] - 1;
      if(ival != data[i] - 1) throw new IllegalArgumentException("categorical value out of range");
      ival += CATOFFS[i];
      if(ival < CATOFFS[i + 1])
        eta += b[ival];
    }
    for(int i = 3; i < b.length-1-205; ++i)
      eta += b[205+i]*data[i];
    eta += b[b.length-1]; // reduce intercept
    double mu = hex.genmodel.GenModel.GLM_identityInv(eta);
    preds[0] = mu;

     */
    double eta = 0.0;
    final double [] b = beta();
    int [] catOffs = dinfo()._catOffsets;
    for(int i = 0; i < catOffs.length-1; ++i) {
      if(chks[i].isNA(row_in_chunk)) {
        eta = Double.NaN;
        break;
      }
      long lval = chks[i].at8(row_in_chunk);
      int ival = (int)lval;
      if(ival != lval) throw new IllegalArgumentException("categorical value out of range");
      if(!_parms._use_all_factor_levels)--ival;
      int from = catOffs[i];
      int to = catOffs[i+1];
      // can get values out of bounds for cat levels not seen in training
      if(ival >= 0 && (ival + from) < catOffs[i+1])
        eta += b[ival+from];
    }
    final int noff = dinfo().numStart() - dinfo()._cats;
    for(int i = dinfo()._cats; i < b.length-1-noff; ++i)
      eta += b[noff+i]*chks[i].atd(row_in_chunk);
    eta += b[b.length-1]; // intercept

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

  @Override protected double[] score0(double[] data, double[] preds){return score0(data,preds,1,0);}
  @Override protected double[] score0(double[] data, double[] preds, double w, double o) {
    double eta = 0.0;
    final double [] b = beta();
    final DataInfo dinfo = _output._dinfo;
    for(int i = 0; i < dinfo._cats; ++i) {
      if(Double.isNaN(data[i])) {
        eta = Double.NaN;
        break;
      }
      int ival = (int) data[i];
      if (ival != data[i]) throw new IllegalArgumentException("categorical value out of range");
      ival += dinfo._catOffsets[i];
      if (!_parms._use_all_factor_levels)
        --ival;
      // can get values out of bounds for cat levels not seen in training
      if (ival >= dinfo._catOffsets[i] && ival < dinfo._catOffsets[i + 1])
        eta += b[ival];
    }
    int noff = dinfo.numStart();
    for(int i = 0; i < dinfo._nums; ++i)
      eta += b[noff+i]*data[dinfo._cats + i];
    eta += b[b.length-1]; // add intercept
    double mu = _parms.linkInv(eta + o);
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
    JCodeGen.toStaticVar(classCtx,"CATOFFS",dinfo()._catOffsets,"Categorical Offsets");
    body.ip("double eta = 0.0;").nl();
    body.ip("final double [] b = BETA;").nl();
    if(!_parms._use_all_factor_levels){ // skip level 0 of all factors
      body.ip("for(int i = 0; i < CATOFFS.length-1; ++i) if(data[i] != 0) {").nl();
      body.ip("  int ival = (int)data[i] - 1;").nl();
      body.ip("  if(ival != data[i] - 1) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
      body.ip("  ival += CATOFFS[i];").nl();
      body.ip("  if(ival < CATOFFS[i + 1])").nl();
      body.ip("    eta += b[ival];").nl();
    } else { // do not skip any levels
      body.ip("for(int i = 0; i < CATOFFS.length-1; ++i) {").nl();
      body.ip("  int ival = (int)data[i];").nl();
      body.ip("  if(ival != data[i]) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
      body.ip("  ival += CATOFFS[i];").nl();
      body.ip("  if(ival < CATOFFS[i + 1])").nl();
      body.ip("    eta += b[ival];").nl();
    }
    body.ip("}").nl();
    final int noff = dinfo().numStart() - dinfo()._cats;
    body.ip("for(int i = ").p(dinfo()._cats).p("; i < b.length-1-").p(noff).p("; ++i)").nl();
    body.ip("  eta += b[").p(noff).p("+i]*data[i];").nl();
    body.ip("eta += b[b.length-1]; // reduce intercept").nl();
    body.ip("double mu = hex.genmodel.GenModel.GLM_").p(_parms._link.toString()).p("Inv(eta");
//    if( _parms._link == hex.glm.GLMModel.GLMParameters.Link.tweedie ) body.p(",").p(_parms._tweedie_link_power);
    body.p(");").nl();
    if( _parms._family == Family.binomial ) {
      body.ip("preds[0] = mu > ").p(_output._threshold).p(" ? 1 : 0); // threshold given by ROC").nl();
      body.ip("preds[1] = 1.0 - mu; // class 0").nl();
      body.ip("preds[2] =       mu; // class 1").nl();
    } else {
      body.ip("preds[0] = mu;").nl();
    }
  }

  @Override protected SB toJavaInit(SB sb, SB fileContext) {
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_output.nclasses()+"; }").nl();
    return sb;
  }
}
