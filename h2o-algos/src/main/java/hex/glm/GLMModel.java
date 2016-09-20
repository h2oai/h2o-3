package hex.glm;

import hex.*;
import hex.DataInfo.Row;
import hex.DataInfo.TransformType;
import hex.api.MakeGLMModelHandler;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import water.*;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.NoSuchElementException;

/**
 * Created by tomasnykodym on 8/27/14.
 */
public class GLMModel extends Model<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  public GLMModel(Key selfKey, GLMParameters parms, GLM job, double [] ymu, double ySigma, double lambda_max, long nobs) {
    super(selfKey, parms, job == null?new GLMOutput():new GLMOutput(job));
    // modelKey, parms, null, Double.NaN, Double.NaN, Double.NaN, -1
    _ymu = ymu;
    _ySigma = ySigma;
    _lambda_max = lambda_max;
    _nobs = nobs;
    _nullDOF = nobs - (parms._intercept?1:0);
  }

  public static class RegularizationPath extends Iced {
    public double []   _lambdas;
    public double []   _explained_deviance_train;
    public double []   _explained_deviance_valid;
    public double [][] _coefficients;
    public double [][] _coefficients_std;
    public String []   _coefficient_names;
  }

  public RegularizationPath getRegularizationPath() {
    RegularizationPath rp = new RegularizationPath();
    rp._coefficient_names = _output._coefficient_names;
    int N = _output._submodels.length;
    int P = _output._dinfo.fullN() + 1;
    rp._lambdas = new double[N];
    rp._coefficients = new double[N][];
    rp._explained_deviance_train = new double[N];
    if (_parms._valid != null)
      rp._explained_deviance_valid = new double[N];
    if (_parms._standardize)
      rp._coefficients_std = new double[N][];
    for (int i = 0; i < N; ++i) {
      Submodel sm = _output._submodels[i];
      rp._lambdas[i] = sm.lambda_value;
      rp._coefficients[i] = sm.getBeta(MemoryManager.malloc8d(P));
      if (_parms._standardize) {
        rp._coefficients_std[i] = rp._coefficients[i];
        rp._coefficients[i] = _output._dinfo.denormalizeBeta(rp._coefficients_std[i]);
      }
      rp._explained_deviance_train[i] = 1 - _output._training_metrics._nobs*sm.devianceTrain/((GLMMetrics)_output._training_metrics).null_deviance();
      if (rp._explained_deviance_valid != null)
        rp._explained_deviance_valid[i] = 1 - _output._validation_metrics._nobs*sm.devianceTest/((GLMMetrics)_output._validation_metrics).null_deviance();
    }
    return rp;
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
    return new GLMMetricBuilder(domain, _ymu, new GLMWeightsFun(_parms), _output.bestSubmodel().rank(), true, _parms._intercept);
  }

  protected double [] beta_internal(){
    if(_parms._family == Family.multinomial)
      return ArrayUtils.flat(_output._global_beta_multinomial);
    return _output._global_beta;
  }
  public double [] beta() { return _output._global_beta;}
  public double [] beta(double lambda) {
    for(int i = 0 ; i < _output._submodels.length; ++i)
      if(_output._submodels[i].lambda_value == lambda)
        return _output._dinfo.denormalizeBeta(_output._submodels[i].getBeta(MemoryManager.malloc8d(_output._dinfo.fullN()+1)));
    throw new RuntimeException("no such lambda value, lambda = " + lambda);
  }
  public double [] beta_std(double lambda) {
    for(int i = 0 ; i < _output._submodels.length; ++i)
      if(_output._submodels[i].lambda_value == lambda)
        return _output._submodels[i].getBeta(MemoryManager.malloc8d(_output._dinfo.fullN()+1));
    throw new RuntimeException("no such lambda value, lambda = " + lambda);
  }
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

  public GLMModel addSubmodel(Submodel sm) {
    _output._submodels = ArrayUtils.append(_output._submodels,sm);
    _output.setSubmodelIdx(_output._submodels.length-1);
    return this;
  }

  public GLMModel updateSubmodel(Submodel sm) {
    assert sm.lambda_value == _output._submodels[_output._submodels.length-1].lambda_value;
    _output._submodels[_output._submodels.length-1] = sm;
    return this;
  }

  public void update(double [] beta, double devianceTrain, double devianceTest,int iter){
    int id = _output._submodels.length-1;
    _output._submodels[id] = new Submodel(_output._submodels[id].lambda_value,beta,iter,devianceTrain,devianceTest);
    _output.setSubmodelIdx(id);
  }

  public GLMModel clone2(){
    GLMModel res = clone();
    res._output = (GLMOutput)res._output.clone();
    return res;
  }


  public static class GLMParameters extends Model.Parameters {

    public String algoName() { return "GLM"; }
    public String fullName() { return "Generalized Linear Modeling"; }
    public String javaName() { return GLMModel.class.getName(); }
    @Override public long progressUnits() { return GLM.WORK_TOTAL; }
    // public int _response; // TODO: the standard is now _response_column in SupervisedModel.SupervisedParameters
    public boolean _standardize = true;
    public Family _family;
    public Link _link = Link.family_default;
    public Solver _solver = Solver.AUTO;
    public double _tweedie_variance_power;
    public double _tweedie_link_power;
    public double [] _alpha = null;
    public double [] _lambda = null;
    public MissingValuesHandling _missing_values_handling = MissingValuesHandling.MeanImputation;
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
    public double _objective_epsilon = -1;
    public double _gradient_epsilon = -1;
    public double _obj_reg = -1;
    public boolean _compute_p_values = false;
    public boolean _remove_collinear_columns = false;
    public String[] _interactions=null;
    public boolean _early_stopping = true;

    public Key<Frame> _beta_constraints = null;
    // internal parameter, handle with care. GLM will stop when there is more than this number of active predictors (after strong rule screening)
    public int _max_active_predictors = -1;
    public boolean _stdOverride; // standardization override by beta constraints

    public void validate(GLM glm) {
      if(_alpha != null && (1 < _alpha[0] || _alpha[0] < 0))
        glm.error("_alpha","alpha parameter must from (inclusive) [0,1] range");
      if(_compute_p_values && _solver != Solver.AUTO && _solver != Solver.IRLSM)
        glm.error("_compute_p_values","P values can only be computed with IRLSM solver, go solver = " + _solver);
      if(_compute_p_values && (_lambda == null || _lambda[0] > 0))
        glm.error("_compute_p_values","P values can only be computed with NO REGULARIZATION (lambda = 0)");
      if(_compute_p_values && _family == Family.multinomial)
        glm.error("_compute_p_values","P values are currently not supported for family=multinomial");
      if(_compute_p_values && _non_negative)
        glm.error("_compute_p_values","P values are currently not supported for family=multinomial");
      if(_weights_column != null && _offset_column != null && _weights_column.equals(_offset_column))
        glm.error("_offset_column", "Offset must be different from weights");
      if(_alpha != null && (_alpha[0] < 0 || _alpha[0] > 1))
        glm.error("_alpha", "Alpha value must be between 0 and 1");
      if(_lambda != null && _lambda[0] < 0)
        glm.error("_lambda", "Lambda value must be >= 0");
      if(_obj_reg != -1 && _obj_reg <= 0)
        glm.error("obj_reg","Must be positive or -1 for default");
      if(_prior != -1 && _prior <= 0 || _prior >= 1)
        glm.error("_prior","Prior must be in (exclusive) range (0,1)");
      if(_prior != -1 && _family != Family.binomial)
        glm.error("_prior","Prior is only allowed with family = binomial.");
      if(_family != Family.tweedie) {
        glm.hide("_tweedie_variance_power","Only applicable with Tweedie family");
        glm.hide("_tweedie_link_power","Only applicable with Tweedie family");
      }
      if(_remove_collinear_columns && !_intercept)
        glm.error("_intercept","Remove colinear columns option is currently not supported without intercept");
      if(_beta_constraints != null) {
        if(_family == Family.multinomial)
          glm.error("beta_constraints","beta constraints are not supported for family = multionomial");
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
      if(!_lambda_search) {
        glm.hide("_lambda_min_ratio", "only applies if lambda search is on.");
        glm.hide("_nlambdas", "only applies if lambda search is on.");
        glm.hide("_early_stopping","only applies if lambda search is on.");
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
      _stopping_rounds = 3;
      _stopping_metric = ScoreKeeper.StoppingMetric.deviance;
      _stopping_tolerance = 1e-4;
    }

    public GLMParameters(Family f){this(f,f.defaultLink);}
    public GLMParameters(Family f, Link l){this(f,l, null, null, 0, 1);}
    public GLMParameters(Family f, Link l, double[] lambda, double[] alpha, double twVar, double twLnk) {
      this(f,l,lambda,alpha,twVar,twLnk,null);
    }

    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha, double twVar, double twLnk, String[] interactions){
      this._lambda = lambda;
      this._alpha = alpha;
      this._tweedie_variance_power = twVar;
      this._tweedie_link_power = twLnk;
      _interactions=interactions;
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

    public final double likelihood(double yr, double ym){ return .5 * deviance(yr,ym);}

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

  public static class GLMWeights {
    public double mu = 0;
    public double w = 1;
    public double z = 0;
    public double l = 0;
    public double dev = Double.NaN;
  }
  public static class GLMWeightsFun extends Iced {
    final Family _family;
    final Link _link;
    final double _var_power;
    final double _link_power;



    public GLMWeightsFun(GLMParameters parms) {this(parms._family,parms._link, parms._tweedie_variance_power, parms._tweedie_link_power);}
    public GLMWeightsFun(Family fam, Link link, double var_power, double link_power) {
      _family = fam;
      _link = link;
      _var_power = var_power;
      _link_power = link_power;
    }

    public final double link(double x) {
      switch(_link) {
        case identity:
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
          return _link_power == 0?Math.log(x):Math.pow(x, _link_power);
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
          return _link_power == 0
            ?1.0/Math.max(2e-16,x)
            :_link_power * Math.pow(x,_link_power-1);
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
          return _link_power == 0
            ?Math.max(2e-16,Math.exp(x))
            :Math.pow(x, 1/ _link_power);
        default:
          throw new RuntimeException("unexpected link function id  " + _link);
      }
    }
    public final double variance(double mu){
      switch(_family) {
        case gaussian:
          return 1;
        case binomial:
          double res = mu * (1 - mu);
          return res < 1e-6?1e-6:res;
        case poisson:
          return mu;
        case gamma:
          return mu * mu;
        case tweedie:
          return Math.pow(mu,_var_power);
        default:
          throw new RuntimeException("unknown family Id " + this._family);
      }
    }

    public final double deviance(double yr, double ym){
      double y1 = yr == 0?.1:yr;
      switch(_family){
        case gaussian:
          return (yr - ym) * (yr - ym);
        case binomial:
          return 2 * ((MathUtils.y_log_y(yr, ym)) + MathUtils.y_log_y(1 - yr, 1 - ym));
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          double theta = _var_power == 1
            ?Math.log(y1/ym)
            :(Math.pow(y1,1.-_var_power) - Math.pow(ym,1 - _var_power))/(1-_var_power);
          double kappa = _var_power == 2
            ?Math.log(y1/ym)
            :(Math.pow(yr,2-_var_power) - Math.pow(ym,2-_var_power))/(2 - _var_power);
          return 2 * (yr * theta - kappa);
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }
    public final double deviance(float yr, float ym){
      return deviance((double)yr,(double)ym);
    }

    public final double likelihood(double yr, double ym) {
      switch (_family) {
        case gaussian:
          return .5 * (yr - ym) * (yr - ym);
        case binomial:
          if (yr == ym) return 0;
          return .5 * deviance(yr, ym);
//          double res = Math.log(1 + Math.exp((1 - 2*yr) * eta));
//          assert Math.abs(res - .5 * deviance(yr,eta,ym)) < 1e-8:res + " != " + .5*deviance(yr,eta,ym) +" yr = "  + yr + ", ym = " + ym + ", eta = " + eta;
//          return res;
//          double res = -yr * eta - Math.log(1 - ym);
//          return res;

        case poisson:
          if (yr == 0) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case gamma:
          if (yr == 0) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          return deviance(yr, ym); //fixme: not really correct, not sure what the likelihood is right now
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }

    public GLMWeights computeWeights(double y, double eta, double off, double w, GLMWeights x) {
      double etaOff = eta + off;
      x.mu = linkInv(etaOff);
      double var = variance(x.mu);//Math.max(1e-5, variance(x.mu)); // avoid numerical problems with 0 variance
      double d = linkDeriv(x.mu);
      x.w = w / (var * d * d);
      x.z = eta + (y - x.mu) * d;
      if(_family == Family.binomial && _link == Link.logit) {
        // use the same likelihood computation as GLMBinomialGradientTask to have exactly the same values for same inputs
        x.l = w * Math.log(1 + Math.exp((etaOff - 2 * y * etaOff)));
        x.dev = 2*x.l;
      } else {
        x.l = w * likelihood(y, x.mu);
        x.dev = w * deviance(y, x.mu);
      }
      return x;
    }
  }

  public static class Submodel extends Iced {
    public final double lambda_value;
    public final int    iteration;
    public final double devianceTrain;
    public final double devianceTest;
    public final int    [] idxs;
    public final double [] beta;

    public double [] getBeta(double [] beta) {
      if(idxs != null){
        for(int i = 0; i < idxs.length; ++i)
          beta[idxs[i]] = this.beta[i];
//        beta[beta.length-1] = this.beta[this.beta.length-1];
      } else
        System.arraycopy(this.beta,0,beta,0,beta.length);
      return beta;
    }

    public int rank(){
      return idxs != null?idxs.length:(ArrayUtils.countNonzeros(beta));
    }

    public Submodel(double lambda , double [] beta, int iteration, double devTrain, double devTest){
      this.lambda_value = lambda;
      this.iteration = iteration;
      this.devianceTrain = devTrain;
      this.devianceTest = devTest;
      int r = 0;
      if(beta != null){
        // grab the indeces of non-zero coefficients
        for(int i = 0; i < beta.length; ++i)if(beta[i] != 0)++r;
        if(r < beta.length) {
          idxs = MemoryManager.malloc4(r);
          int j = 0;
          for (int i = 0; i < beta.length; ++i)
            if (beta[i] != 0) idxs[j++] = i;
          this.beta = ArrayUtils.select(beta,idxs);
        } else {
          this.beta = beta.clone();
          idxs = null;
        }
      } else {
        this.beta = null;
        idxs = null;
      }
    }
  }

  public final double    _lambda_max;
  public final double [] _ymu;
  public final long    _nullDOF;
  public final double    _ySigma;
  public final long      _nobs;

  private static String[] binomialClassNames = new String[]{"0", "1"};

  @Override
  protected String[][] scoringDomains(){
    String [][] domains = _output._domains;
    if(_parms._family == Family.binomial && _output._domains[_output._dinfo.responseChunkId(0)] == null) {
      domains = domains.clone();
      domains[_output._dinfo.responseChunkId(0)] = binomialClassNames;
    }
    return domains;
  }

  public void setZValues(double [] zValues, double dispersion, boolean dispersionEstimated) {
    _output._zvalues = zValues;
    _output._dispersion = dispersion;
    _output._dispersionEstimated = dispersionEstimated;
  }
  public static class GLMOutput extends Model.Output {
    Submodel[] _submodels = new Submodel[0];
    DataInfo _dinfo;
    String[] _coefficient_names;
    public int _best_lambda_idx; // lambda which minimizes deviance on validation (if provided) or train (if not)
    public int _lambda_1se = -1; // lambda_best + sd(lambda); only applicable if running lambda search with nfold
    public int _selected_lambda_idx; // lambda which minimizes deviance on validation (if provided) or train (if not)
    public double lambda_best(){return _submodels.length == 0 ? -1 : _submodels[_best_lambda_idx].lambda_value;}
    public double lambda_1se(){return _lambda_1se == -1?-1:_submodels.length == 0 ? -1 : _submodels[_lambda_1se].lambda_value;}
    public double lambda_selected(){return _submodels[_selected_lambda_idx].lambda_value;}
    double[] _global_beta;
    private double[] _zvalues;
    private double _dispersion;
    private boolean _dispersionEstimated;

    public boolean hasPValues(){return _zvalues != null;}
    public double [] stdErr(){
      double [] res = _zvalues.clone();
      for(int i = 0; i < res.length; ++i)
        res[i] = _global_beta[i]/_zvalues[i];
      return res;
    }

    @Override
    protected long checksum_impl() {
      long d = _global_beta == null?1:Arrays.hashCode(_global_beta);
      return d*super.checksum_impl();
    }
    public double [] zValues(){return _zvalues.clone();}
    public double [] pValues(){
      double [] res = zValues();
      RealDistribution rd = _dispersionEstimated?new TDistribution(_training_metrics.residual_degrees_of_freedom()):new NormalDistribution();
      for(int i = 0; i < res.length; ++i)
        res[i] = 2*rd.cumulativeProbability(-Math.abs(res[i]));
      return res;
    }
    double[][] _global_beta_multinomial;
    final int _nclasses;
    public boolean _binomial;
    public boolean _multinomial;

    public int rank() { return _submodels[_selected_lambda_idx].rank();}

    public boolean isStandardized() {
      return _dinfo._predictor_transform == TransformType.STANDARDIZE;
    }



    public String[] coefficientNames() {
      return _coefficient_names;
    }

    // GLM is always supervised
    public boolean isSupervised() { return true; }

    @Override public String[] interactions() { return _dinfo._interactionColumns; }
    public static Frame expand(Frame fr, String[] interactions, boolean useAll, boolean standardize, boolean skipMissing) {
      return MakeGLMModelHandler.oneHot(fr,interactions,useAll,standardize,false,skipMissing);
    }

    public GLMOutput(DataInfo dinfo, String[] column_names, String[][] domains, String[] coefficient_names, boolean binomial) {
      super(dinfo._weights, dinfo._offset, dinfo._fold);
      _dinfo = dinfo.clone();
      _dinfo._adaptedFrame = new Frame(dinfo._adaptedFrame.names().clone(),dinfo._adaptedFrame.vecs().clone());
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
      _dinfo = glm._dinfo.clone();
      _dinfo._adaptedFrame = new Frame(glm._dinfo._adaptedFrame.names().clone(),glm._dinfo._adaptedFrame.vecs().clone());
      if(!glm.hasWeightCol())
        _dinfo.dropWeights();
      String[] cnames = glm._dinfo.coefNames();
      String [] names = _dinfo._adaptedFrame._names;
      String [][] domains = _dinfo._adaptedFrame.domains();
      int id = glm._generatedWeights == null?-1:ArrayUtils.find(names, glm._generatedWeights);
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

    public Submodel pickBestModel() {
      int bestId = 0;
      Submodel best = _submodels[0];
      for(int i = 1; i < _submodels.length; ++i) {
        Submodel sm = _submodels[i];
        if(!(sm.devianceTest > best.devianceTest) && sm.devianceTrain < best.devianceTrain){
          bestId = i;
          best = sm;
        }
      }
      setSubmodelIdx(_best_lambda_idx = bestId);
      return best;
    }

    public double[] getNormBeta() {return _submodels[_selected_lambda_idx].getBeta(MemoryManager.malloc8d(_dinfo.fullN()+1));}

    public double[][] getNormBetaMultinomial() {
      return getNormBetaMultinomial(_selected_lambda_idx);
    }

    public double[][] getNormBetaMultinomial(int idx) {
      if(_submodels == null || _submodels.length == 0) // no model yet
        return null;
      double [][] res = new double[nclasses()][];
      Submodel sm = _submodels[idx];
      int N = _dinfo.fullN()+1;
      double [] beta = sm.beta;
      if(sm.idxs != null)
        beta = ArrayUtils.expandAndScatter(beta,nclasses()*(_dinfo.fullN()+1),sm.idxs);
      for(int i = 0; i < res.length; ++i)
        res[i] = Arrays.copyOfRange(beta,i*N,(i+1)*N);
      return res;
    }

    public double[][] get_global_beta_multinomial(){return _global_beta_multinomial;}


    public void setSubmodelIdx(int l){
      _selected_lambda_idx = l;
      if(_multinomial) {
        _global_beta_multinomial = getNormBetaMultinomial(l);
        for(int i = 0; i < _global_beta_multinomial.length; ++i)
          _global_beta_multinomial[i] = _dinfo.denormalizeBeta(_global_beta_multinomial[i]);
      } else {
        if (_global_beta == null)
          _global_beta = MemoryManager.malloc8d(_coefficient_names.length);
        else
          Arrays.fill(_global_beta, 0);
        _submodels[l].getBeta(_global_beta);
        _global_beta = _dinfo.denormalizeBeta(_global_beta);
      }
    }
    public double [] beta() { return _global_beta;}
    public Submodel bestSubmodel(){ return _submodels[_best_lambda_idx];}

    public void setSubmodel(double lambdaCVEstimate) {
      for(int i = 0; i < _submodels.length; ++i)
        if(_submodels[i] != null && _submodels[i].lambda_value == lambdaCVEstimate) {
          setSubmodelIdx(i);
          return;
        }
      throw new NoSuchElementException("has no model for lambda = " + lambdaCVEstimate);
    }

    public Submodel getSubmodel(double lambdaCVEstimate) {
      for(int i = 0; i < _submodels.length; ++i)
        if(_submodels[i] != null && _submodels[i].lambda_value == lambdaCVEstimate) {
          return _submodels[i];
        }
      return null;
    }
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
      regularization = regularization + MathUtils.roundToNDigits(_parms._lambda[_output._selected_lambda_idx], 4) + " )";
    }
    _output._model_summary.set(0, 2, regularization);
    int lambdaSearch = 0;
    if (_parms._lambda_search) {
      lambdaSearch = 1;
      _output._model_summary.set(0, 3, "nlambda = " + _parms._nlambdas + ", lambda.max = " + MathUtils.roundToNDigits(_lambda_max, 4) + ", lambda.min = "  + MathUtils.roundToNDigits(_output.lambda_best(), 4) + ", lambda.1se = " +  MathUtils.roundToNDigits(_output.lambda_1se(), 4));
    }
    int intercept = _parms._intercept ? 1 : 0;
    if(_output.nclasses() > 2) {
      _output._model_summary.set(0, 3 + lambdaSearch,_output.bestSubmodel().beta.length);
    } else {
      _output._model_summary.set(0, 3 + lambdaSearch, beta().length - 1);
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

  public double [] scoreRow(Row r, double o, double [] preds) {
    if(_parms._family == Family.multinomial) {
      double[] eta = _eta.get();
      if(eta == null || eta.length < _output.nclasses()) _eta.set(eta = MemoryManager.malloc8d(_output.nclasses()));
      final double[][] bm = _output._global_beta_multinomial;
      double sumExp = 0;
      double maxRow = 0;
      for (int c = 0; c < bm.length; ++c) {
        eta[c] = r.innerProduct(bm[c]) + o;
        if(eta[c] > maxRow)
          maxRow = eta[c];
      }
      for (int c = 0; c < bm.length; ++c)
        sumExp += eta[c] = Math.exp(eta[c]-maxRow); // intercept
      sumExp = 1.0 / sumExp;
      for (int c = 0; c < bm.length; ++c)
        preds[c + 1] = eta[c] * sumExp;
      preds[0] = ArrayUtils.maxIndex(eta);
    } else {
      double mu = _parms.linkInv(r.innerProduct(beta()) + o);
      if (_parms._family == Family.binomial) { // threshold for prediction
        preds[0] = mu >= defaultThreshold()?1:0;
        preds[1] = 1.0 - mu; // class 0
        preds[2] = mu; // class 1
      } else
        preds[0] = mu;
    }
    return preds;
  }

  private static ThreadLocal<double[]> _eta = new ThreadLocal<>();

  @Override protected double[] score0(double[] data, double[] preds){return score0(data,preds,1,0);}
  @Override protected double[] score0(double[] data, double[] preds, double w, double o) {
    if(_parms._family == Family.multinomial) {
      if(o != 0) throw H2O.unimpl("Offset is not implemented for multinomial.");
      double[] eta = _eta.get();
      if(eta == null || eta.length < _output.nclasses()) _eta.set(eta = MemoryManager.malloc8d(_output.nclasses()));
      final double[][] bm = _output._global_beta_multinomial;
      double sumExp = 0;
      double maxRow = 0;
      for (int c = 0; c < bm.length; ++c) {
        double e = bm[c][bm[c].length-1];
        double [] b = bm[c];
        for(int i = 0; i < _output._dinfo._cats; ++i)
          e += b[_output._dinfo.getCategoricalId(i,data[i])];
        int coff = _output._dinfo._cats;
        int boff = _output._dinfo.numStart();
        for(int i = 0; i < _output._dinfo._nums; ++i) {
          double d = data[coff+i];
          if(!_output._dinfo._skipMissing && Double.isNaN(d))
            d = _output._dinfo._numMeans[i];
          e += d*b[boff+i];
        }
        if(e > maxRow) maxRow = e;
        eta[c] = e;
      }
      for (int c = 0; c < bm.length; ++c)
        sumExp += eta[c] = Math.exp(eta[c]-maxRow); // intercept
      sumExp = 1.0 / sumExp;
      for (int c = 0; c < bm.length; ++c)
        preds[c + 1] = eta[c] * sumExp;
      preds[0] = ArrayUtils.maxIndex(eta);
    } else {
      double[] b = beta();
      double eta = b[b.length - 1] + o; // intercept + offset
      for (int i = 0; i < _output._dinfo._cats && !Double.isNaN(eta); ++i) {
        int l = _output._dinfo.getCategoricalId(i, data[i]);
        if (l >= 0) eta += b[l];
      }
      int numStart = _output._dinfo.numStart();
      int ncats = _output._dinfo._cats;
      for (int i = 0; i < _output._dinfo._nums && !Double.isNaN(eta); ++i) {
        double d = data[ncats + i];
        if (!_output._dinfo._skipMissing && Double.isNaN(d))
          d = _output._dinfo._numMeans[i];
        eta += b[numStart + i] * d;
      }
      double mu = _parms.linkInv(eta);
      if (_parms._family == Family.binomial) { // threshold for prediction
        preds[0] = mu >= defaultThreshold()?1:0;
        preds[1] = 1.0 - mu; // class 0
        preds[2] = mu; // class 1
      } else
        preds[0] = mu;
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
        JCodeGen.toClassWithArray(out, "public static", "BETA", beta_internal()); // "The Coefficients"
        JCodeGen.toClassWithArray(out, "static", "NUM_MEANS", _output._dinfo._numMeans,"Imputed numeric values");
        JCodeGen.toClassWithArray(out, "static", "CAT_MODES", _output._dinfo.catModes(),"Imputed categorical values.");
        JCodeGen.toStaticVar(out, "CATOFFS", dinfo()._catOffsets, "Categorical Offsets");
      }
    });
    body.ip("final double [] b = BETA.VALUES;").nl();
    if(_parms._missing_values_handling == MissingValuesHandling.MeanImputation){
      body.ip("for(int i = 0; i < " + _output._dinfo._cats + "; ++i) if(Double.isNaN(data[i])) data[i] = CAT_MODES.VALUES[i];").nl();
      body.ip("for(int i = 0; i < " + _output._dinfo._nums + "; ++i) if(Double.isNaN(data[i + " + _output._dinfo._cats + "])) data[i+" + _output._dinfo._cats + "] = NUM_MEANS.VALUES[i];").nl();
    }
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
      body.ip("eta += b[").p(noff).p("+i]*data[i];").nl();
      body.ip("eta += b[b.length-1]; // reduce intercept").nl();
      if(_parms._family != Family.tweedie)
        body.ip("double mu = hex.genmodel.GenModel.GLM_").p(_parms._link.toString()).p("Inv(eta");
      else
        body.ip("double mu = hex.genmodel.GenModel.GLM_tweedieInv(eta," + _parms._tweedie_link_power);
      body.p(");").nl();
      if (_parms._family == Family.binomial) {
        body.ip("preds[0] = (mu >= ").p(defaultThreshold()).p(") ? 1 : 0").p("; // threshold given by ROC").nl();
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




  private GLMScore makeScoringTask(Frame adaptFrm, boolean generatePredictions, Job j){
    // Build up the names & domains.
    final boolean computeMetrics = adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad();
    String [] domain = _output.nclasses()<=1 ? null : !computeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();
    // Score the dataset, building the class distribution & predictions
    return new GLMScore(j, this, _output._dinfo.scoringInfo(adaptFrm),domain,computeMetrics, generatePredictions);
  }
  /** Score an already adapted frame.  Returns a new Frame with new result
   *  vectors, all in the DKV.  Caller responsible for deleting.  Input is
   *  already adapted to the Model's domain, so the output is also.  Also
   *  computes the metrics for this frame.
   *
   * @param adaptFrm Already adapted frame
   * @return A Frame containing the prediction column, and class distribution
   */
  @Override
  protected Frame predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j) {
    String [] names = makeScoringNames();
    String [][] domains = new String[names.length][];
    GLMScore gs = makeScoringTask(adaptFrm,true,j).doAll(names.length,Vec.T_NUM,adaptFrm);
    if (gs._computeMetrics)
      gs._mb.makeModelMetrics(this, fr, adaptFrm, gs.outputFrame());
    domains[0] = gs._domain;
    return gs.outputFrame(Key.<Frame>make(destination_key),names, domains);
  }

  /** Score an already adapted frame.  Returns a MetricBuilder that can be used to make a model metrics.
   * @param adaptFrm Already adapted frame
   * @return MetricBuilder
   */
  @Override
  protected ModelMetrics.MetricBuilder scoreMetrics(Frame adaptFrm) {
    return makeScoringTask(adaptFrm,false,null).doAll(adaptFrm)._mb;
  }

}
