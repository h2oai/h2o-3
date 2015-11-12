package hex.glm;

import java.util.Arrays;
import java.util.HashMap;

import hex.DataInfo;
import hex.DataInfo.TransformType;
import hex.Model;
import hex.ModelMetrics;
import hex.glm.GLMModel.GLMParameters.Family;
import water.DKV;
import water.H2O;
import water.Iced;
import water.Key;
import water.MemoryManager;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.JCodeGen;
import water.util.Log;
import water.util.MathUtils;
import water.util.SBPrintStream;
import water.util.TwoDimTable;

/**
 * Created by tomasnykodym on 8/27/14.
 */
public class GLMModel extends Model<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  public GLMModel(Key selfKey, GLMParameters parms, GLM job, double [] ymu, double ySigma, double lambda_max, long nobs, boolean hasWeights, boolean hasOffset) {
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
      domain = binomialClassNames;
    return new GLMValidation(domain, _ymu, _parms, _output.bestSubmodel().rank(), _output._threshold, true, _parms._intercept);
  }

  protected double [] beta_internal(){
    if(_parms._family == Family.multinomial)
      return ArrayUtils.flat(_output._global_beta_multinomial);
    return _output._global_beta;
  }
  public double [] beta() { return _output._global_beta;}
  public String [] names(){ return _output._names;}

  @Override
  public double deviance(double w, double y, double f) {
    if (w == 0) {
      return 0;
    } else if (w == 1) {
      return _parms.deviance(y, f);
    } else {
      return Double.NaN; //TODO: add deviance(w, y, f)
    }
  }

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
    public boolean _intercept = true;
    public double _beta_epsilon = 1e-4;
    public double _objective_epsilon = 1e-5;
    public double _gradient_epsilon = 1e-5;
    public double _obj_reg = -1;

    public Key<Frame> _beta_constraints = null;
    // internal parameter, handle with care. GLM will stop when there is more than this number of active predictors (after strong rule screening)
    public int _max_active_predictors = -1;

    public void validate(GLM glm) {
      if(_weights_column != null && _offset_column != null && _weights_column.equals(_offset_column))
        glm.error("_offset_column", "Offset must be different from weights");
      if(_lambda_search)
        if (glm.nFoldCV())
          glm.error("_lambda_search", "Lambda search is not currently supported in conjunction with N-fold cross-validation");
        if(_nlambdas == -1)
          _nlambdas = 100;
        else
          _exactLambdas = false;
      if(_family != Family.tweedie) {
        glm.hide("_tweedie_variance_power","Only applicable with Tweedie family");
        glm.hide("_tweedie_link_power","Only applicable with Tweedie family");
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
          case multinomial:
            if(_link != Link.multinomial)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only multinomial link allowed for family=multinomial.");
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
        case multinomial:
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
//        case multinomial:
          return x;
        case logit:
          assert 0 <= x && x <= 1:"x out of bounds, expected <0,1> range, got " + x;
          return Math.log(x / (1 - x));
        case multinomial:
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
//        case multinomial:
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
//        case multinomial: // should not be used
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
      gamma(Link.inverse), multinomial(Link.multinomial), tweedie(Link.tweedie);
      public final Link defaultLink;
      Family(Link link){defaultLink = link;}
    }
    public static enum Link {family_default, identity, logit, log, inverse, tweedie, multinomial}

    public static enum Solver {AUTO, IRLSM, L_BFGS, COORDINATE_DESCENT_NAIVE, COORDINATE_DESCENT}

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
    public final double [][] betaMultinomial;


    public int rank(){
      if(betaMultinomial != null) {
        int res = 0;
        for(double [] ds:betaMultinomial)
          for(double d:ds)
            if(d != 0)++res;
        return res;
      }
      return idxs != null?idxs.length+1:beta.length;
    }

    /**
     * Constructor for multinomial submodel
     * @param lambda
     * @param beta
     * @param iteration
     * @param devTrain
     * @param devTest
     */
    public Submodel(double lambda , double [][] beta, int [] idxs, int iteration, double devTrain, double devTest){
      this.lambda_value = lambda;
      this.iteration = iteration;
      this.devianceTrain = devTrain;
      this.devianceTest = devTest;
      this.beta = null;
      // grab the indeces of non-zero coefficients
      this.betaMultinomial = beta;
      this.idxs = idxs;
      assert idxs == null || idxs.length == beta[0].length-1:"idxs = " + Arrays.toString(idxs) + ", beta = " + Arrays.toString(betaMultinomial[0]);
    }


    public Submodel(double lambda , double [] beta, int iteration, double devTrain, double devTest){
      this.lambda_value = lambda;
      this.iteration = iteration;
      this.devianceTrain = devTrain;
      this.devianceTest = devTest;
      this.betaMultinomial = null;
      int r = 0;
      if(beta != null){
        // grab the indeces of non-zero coefficients
        for(int i = 0; i < beta.length-1; ++i)if(beta[i] != 0)++r;
        idxs = MemoryManager.malloc4(r);
        int j = 0;
        for(int i = 0; i < beta.length-1; ++i)
          if(beta[i] != 0)idxs[j++] = i;
        j = 0;
        this.beta = MemoryManager.malloc8d(idxs.length+1);
        for(int i:idxs)
          this.beta[j++] = beta[i];
        this.beta[this.beta.length-1] = beta[beta.length-1]; // intercept
      } else {
        this.beta = null;
        idxs = null;
      }
    }
  }

  public final double    _lambda_max;
  public final double [] _ymu;
  public final double    _ySigma;
  public final long      _nobs;

  private static String[] binomialClassNames = new String[]{"0", "1"};

  public static class GLMOutput extends Model.Output {
    Submodel[] _submodels;
    DataInfo _dinfo;
    String[] _coefficient_names;
    public int _best_lambda_idx;

    double _threshold;
    double[] _global_beta;
    double[][] _global_beta_multinomial;
    final int _nclasses;
    public boolean _binomial;
    public boolean _multinomial;

    public int rank() { return _submodels[_best_lambda_idx].rank();}

    public boolean isStandardized() {
      return _dinfo._predictor_transform == TransformType.STANDARDIZE;
    }



    public String[] coefficientNames() {
      return _coefficient_names;
    }

    // GLM is always supervised
    public boolean isSupervised() { return true; }

    public GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial) {
      super(dinfo._weights, dinfo._offset, dinfo._fold);
      _dinfo = dinfo;
      _names = column_names;
      _domains = domains;
      _coefficient_names = coefficient_names;
      _binomial = binomial;
      _nclasses = binomial?2:1;

      if(_binomial && domains[domains.length-1] != null) {
        assert domains[domains.length - 1].length == 2:"Unexpected domains " + Arrays.toString(domains);
        binomialClassNames = domains[domains.length - 1];
      }
    }

    public GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial, double[] beta) {
      this(dinfo,column_names,domains,coefficient_names,binomial);
      assert !ArrayUtils.hasNaNsOrInfs(beta);
      _global_beta=beta;
      _submodels = new Submodel[]{new Submodel(0,beta,-1,Double.NaN,Double.NaN)};
    }

    public GLMOutput() {_isSupervised = true; _nclasses = -1;}

    public GLMOutput(GLM glm) {
      super(glm);
      _dinfo = glm._dinfo;
      if(!glm.hasWeightCol()){
        _dinfo = (DataInfo)_dinfo.clone();
        _dinfo._adaptedFrame = new Frame(_dinfo._adaptedFrame.names().clone(),_dinfo._adaptedFrame.vecs().clone());
        _dinfo.dropWeights();
      }
      String[] cnames = glm._dinfo.coefNames();
      String [] names = _dinfo._adaptedFrame._names;
      String [][] domains = _dinfo._adaptedFrame.domains();
      int id = ArrayUtils.find(names, glm._generatedWeights);
      if(id >= 0) {
        String [] ns = new String[names.length-1];
        String[][] ds = new String[domains.length-1][];
        System.arraycopy(names,0,ns,0,id);
        System.arraycopy(domains,0,ds,0,id);
        System.arraycopy(names,id+1,ns,id,ns.length-id);
        System.arraycopy(domains,id+1,ds,id,ds.length-id);
        names = ns;
        domains = ds;
      }
      _names = names;
      _domains = domains;
      _coefficient_names = Arrays.copyOf(cnames, cnames.length + 1);
      _coefficient_names[_coefficient_names.length-1] = "Intercept";
      _binomial = glm._parms._family == Family.binomial;
      _nclasses = glm.nclasses();
      _multinomial = _nclasses > 2;
    }

    @Override
    public int nclasses() {
      return _nclasses;
    }



    @Override
    public String[] classNames() {
      String [] res = super.classNames();
      if(res == null && _binomial)
        return binomialClassNames;
      return res;
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

    public double[][] getNormBetaMultinomial() {
      return getNormBetaMultinomial(_best_lambda_idx);
    }

    public double[][] getNormBetaMultinomial(int idx) {
      double [][] res = new double[nclasses()][];
      Submodel sm = _submodels[idx];
      for(int i = 0; i < res.length; ++i) {
        if (sm.idxs == null) res[i] = sm.betaMultinomial[i].clone();
        else {
          res[i] = MemoryManager.malloc8d(_dinfo.fullN() + 1);
          int j = 0;
          for (int id : sm.idxs)
            res[i][id] = sm.betaMultinomial[i][j++];
          res[i][_dinfo.fullN()] = sm.betaMultinomial[i][sm.betaMultinomial[i].length-1];
        }
      }
      return res;
    }

    public double[][] get_global_beta_multinomial(){return _global_beta_multinomial;}

    public void getBeta(int l, double [] beta) {
      assert beta.length == _dinfo.fullN()+1;
      int k = 0;
      for(int i:_submodels[l].idxs)
        beta[i] = _submodels[l].beta[k++];
      beta[beta.length-1] = _submodels[l].beta[_submodels[l].beta.length-1];
    }
    public void setSubmodelIdx(int l){
      _best_lambda_idx = l;
      if(_multinomial) {
        _global_beta_multinomial = getNormBetaMultinomial(l);
        for(int i = 0; i < _global_beta_multinomial.length; ++i)
          _global_beta_multinomial[i] = _dinfo.denormalizeBeta(_global_beta_multinomial[i]);
      } else {
        if (_global_beta == null)
          _global_beta = MemoryManager.malloc8d(_coefficient_names.length);
        else
          Arrays.fill(_global_beta, 0);
        getBeta(l, _global_beta);
        _global_beta = _dinfo.denormalizeBeta(_global_beta);
      }
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
    if(_output.nclasses() > 2) {
      _output._model_summary.set(0, 3 + lambdaSearch,_output.bestSubmodel().betaMultinomial[0].length*_output.nclasses());
    } else {
      _output._model_summary.set(0, 3 + lambdaSearch, beta().length);
    }
    _output._model_summary.set(0, 4 + lambdaSearch, Integer.toString(_output.rank() - intercept));
    _output._model_summary.set(0, 5 + lambdaSearch, Integer.valueOf(iter));
    _output._model_summary.set(0, 6 + lambdaSearch, train.toString());
    return _output._model_summary;
  }


  @Override public long checksum_impl(){
    if(_parms._train == null) return 0;
    return super.checksum_impl();
  }

  private double [] scoreMultinomial(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
    double[] eta = MemoryManager.malloc8d(_output.nclasses());
    final double[][] b = _output._global_beta_multinomial;
    final int P = b[0].length;
    int[] catOffs = dinfo()._catOffsets;
    for (int i = 0; i < catOffs.length - 1; ++i) {
      if (chks[i].isNA(row_in_chunk)) {
        Arrays.fill(eta, Double.NaN);
        break;
      }
      long lval = chks[i].at8(row_in_chunk);
      int ival = (int) lval;
      if (ival != lval) throw new IllegalArgumentException("categorical value out of range");
      if (!_parms._use_all_factor_levels) --ival;
      int from = catOffs[i];
      int to = catOffs[i + 1];
      // can get values out of bounds for cat levels not seen in training
      if (ival >= 0 && (ival + from) < catOffs[i + 1])
        for (int j = 0; j < _output.nclasses(); ++j)
          eta[j] += b[j][ival + from];
    }
    final int noff = dinfo().numStart() - dinfo()._cats;
    for (int i = dinfo()._cats; i < b.length - 1 - noff; ++i) {
      double d = chks[i].atd(row_in_chunk);
      for (int j = 0; j < _output.nclasses(); ++j)
        eta[j] += b[j][noff + i] * d;
    }
    double sumExp = 0;
    double max_row = 0;
    for (int j = 0; j < _output.nclasses(); ++j) {
      eta[j] += b[j][P - 1];
      if(eta[j] > max_row)
        max_row = eta[j];
    }
    for (int j = 0; j < _output.nclasses(); ++j)
      sumExp += eta[j] = Math.exp(eta[j]-max_row); // intercept
    sumExp = 1.0 / sumExp;
    for (int i = 0; i < eta.length; ++i)
      preds[i + 1] = eta[i] * sumExp;
    preds[0] = ArrayUtils.maxIndex(eta);
    return preds;
  }

  @Override
  public double[] score0(Chunk[] chks, int row_in_chunk, double[] tmp, double[] preds) {
    if(_parms._family == Family.multinomial)
      return scoreMultinomial(chks,row_in_chunk,tmp,preds);
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

  private double [] scoreMultinomial(double[] data, double[] preds, double w, double o) {
    double [] eta = MemoryManager.malloc8d(_output.nclasses());
    final double [][] b = _output._global_beta_multinomial;
    final int P = b[0].length;
    final DataInfo dinfo = _output._dinfo;
    for(int i = 0; i < dinfo._cats; ++i) {
      if(Double.isNaN(data[i])) {
        Arrays.fill(eta,Double.NaN);
        break;
      }
      int ival = (int) data[i];
      if (ival != data[i]) throw new IllegalArgumentException("categorical value out of range");
      ival += dinfo._catOffsets[i];
      if (!_parms._use_all_factor_levels)
        --ival;
      // can get values out of bounds for cat levels not seen in training
      if (ival >= dinfo._catOffsets[i] && ival < dinfo._catOffsets[i + 1])
        for(int j = 0; j < eta.length; ++j)
          eta[j] += b[j][ival];
    }
    int noff = dinfo.numStart();
    for(int i = 0; i < dinfo._nums; ++i) {
      double d = data[dinfo._cats + i];
      for (int j = 0; j < eta.length; ++j)
        eta[j] += b[j][noff + i] * d;
    }
    double sumExp = 0;
    double max_row = 0;
    for (int j = 0; j < eta.length; ++j) {
      eta[j] += b[j][P - 1];
      if(eta[j] > max_row)
        max_row = eta[j];
    }
    for (int j = 0; j < eta.length; ++j)
      sumExp += (eta[j] = Math.exp(eta[j]-max_row));
    sumExp = 1.0/sumExp;
    preds[0] = ArrayUtils.maxIndex(eta);
    for(int i = 0; i < eta.length; ++i)
      preds[1+i] = eta[i]*sumExp;
    return preds;
  }

  @Override protected double[] score0(double[] data, double[] preds, double w, double o) {
    if(_parms._family == Family.multinomial)
      return scoreMultinomial(data, preds, w, o);
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

  @Override protected void toJavaPredictBody(SBPrintStream body,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    // Generate static fields
    classCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        JCodeGen.toClassWithArray(out, "static", "BETA", beta_internal()); // "The Coefficients"
        JCodeGen.toStaticVar(out, "CATOFFS", dinfo()._catOffsets, "Categorical Offsets");
      }
    });

    body.ip("final double [] b = BETA.VALUES;").nl();

    if(_parms._family != Family.multinomial) {
      body.ip("double eta = 0.0;").nl();
      if (!_parms._use_all_factor_levels) { // skip level 0 of all factors
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
      if (_parms._family == Family.binomial) {
        body.ip("preds[0] = (mu > ").p(_output._threshold).p(") ? 1 : 0").p("; // threshold given by ROC").nl();
        body.ip("preds[1] = 1.0 - mu; // class 0").nl();
        body.ip("preds[2] =       mu; // class 1").nl();
      } else {
        body.ip("preds[0] = mu;").nl();
      }
    } else {
      int P = _output._global_beta_multinomial[0].length;
      body.ip("preds[0] = 0;").nl();
      body.ip("for(int c = 0; c < " + _output._nclasses + "; ++c){").nl();
      body.ip("  preds[c+1] = 0;").nl();
      if(dinfo()._cats > 0) {
        if (!_parms._use_all_factor_levels) { // skip level 0 of all factors
          body.ip("  for(int i = 0; i < CATOFFS.length-1; ++i) if(data[i] != 0) {").nl();
          body.ip("    int ival = (int)data[i] - 1;").nl();
          body.ip("    if(ival != data[i] - 1) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
          body.ip("    ival += CATOFFS[i];").nl();
          body.ip("    if(ival < CATOFFS[i + 1])").nl();
          body.ip("      preds[c+1] += b[ival+c*" + P + "];").nl();
        } else { // do not skip any levels
          body.ip("  for(int i = 0; i < CATOFFS.length-1; ++i) {").nl();
          body.ip("    int ival = (int)data[i];").nl();
          body.ip("    if(ival != data[i]) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
          body.ip("    ival += CATOFFS[i];").nl();
          body.ip("    if(ival < CATOFFS[i + 1])").nl();
          body.ip("      preds[c+1] += b[ival+c*" + P + "];").nl();
        }
        body.ip("  }").nl();
      }
      final int noff = dinfo().numStart();
      body.ip("  for(int i = 0; i < " + dinfo()._nums + "; ++i)").nl();
      body.ip("    preds[c+1] += b[" + noff + "+i + c*" + P + "]*data[i];").nl();
      body.ip("  preds[c+1] += b[" + (P-1) +" + c*" + P + "]; // reduce intercept").nl();
      body.ip("}").nl();
      body.ip("double max_row = 0;").nl();
      body.ip("for(int c = 1; c < preds.length; ++c) if(preds[c] > max_row) max_row = preds[c];").nl();
      body.ip("double sum_exp = 0;").nl();
      body.ip("for(int c = 1; c < preds.length; ++c) { sum_exp += (preds[c] = Math.exp(preds[c]-max_row));}").nl();
      body.ip("sum_exp = 1/sum_exp;").nl();
      body.ip("double max_p = 0;").nl();
      body.ip("for(int c = 1; c < preds.length; ++c) if((preds[c] *= sum_exp) > max_p){ max_p = preds[c]; preds[0] = c-1;};").nl();
    }
  }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_output.nclasses()+"; }").nl();
    return sb;
  }
}
