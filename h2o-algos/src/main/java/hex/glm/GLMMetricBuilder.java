package hex.glm;

import hex.*;
import hex.ModelMetrics.MetricBuilder;
import hex.ModelMetricsBinomial.MetricBuilderBinomial;
import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.ModelMetricsMultinomial.MetricBuilderMultinomial;
import hex.ModelMetricsRegression.MetricBuilderRegression;
import hex.ModelMetricsSupervised.MetricBuilderSupervised;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMWeightsFun;
import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;

/**
 * Class for GLMValidation.
 *
 * @author tomasnykodym
 *
 */
public class GLMMetricBuilder extends MetricBuilderSupervised<GLMMetricBuilder> {
  double residual_deviance;
  double null_devince;
  long _nobs;
  double _aic;// internal AIC used only for poisson family!
  private double _aic2;// internal AIC used only for poisson family!
  final GLMModel.GLMWeightsFun _glmf;
  final private int _rank;
  MetricBuilder _metricBuilder;
  final boolean _intercept;
  private final double [] _ymu;

  final boolean _computeMetrics;
  public GLMMetricBuilder(String[] domain, double [] ymu, GLMWeightsFun glmf, int rank, boolean computeMetrics, boolean intercept){
    super(domain == null?1:domain.length, domain);
    _rank = rank;
    _glmf = glmf;
    _computeMetrics = computeMetrics;
    _intercept = intercept;
    _ymu = ymu;
    if(_computeMetrics) {
      switch(_glmf._family){
        case binomial:
          _metricBuilder = new MetricBuilderBinomial(domain);
          break;
        case multinomial:
          _metricBuilder = new MetricBuilderMultinomial(domain.length,domain);
          ((MetricBuilderMultinomial)_metricBuilder)._priorDistribution = ymu;
          break;
        default:
          _metricBuilder = new MetricBuilderRegression();
          break;
      }
    }
  }


  public double explainedDev(){
    throw H2O.unimpl();
  }

  @Override public double[] perRow(double ds[], float[] yact, Model m) {
    return perRow(ds, yact, 1, 0, m);
  }

  @Override public double[] perRow(double ds[], float[] yact, double weight, double offset, Model m) {
    if(weight == 0)return ds;
    _metricBuilder.perRow(ds,yact,weight,offset,m);
    if(!ArrayUtils.hasNaNsOrInfs(ds) && !ArrayUtils.hasNaNsOrInfs(yact)) {
      if(_glmf._family == Family.multinomial)
        add2(yact[0], ds, weight, offset);
      else if (_glmf._family == Family.binomial)
        add2(yact[0], ds[2], weight, offset);
      else
        add2(yact[0], ds[0], weight, offset);
    }
    return ds;
  }

//  public GLMValidation(Key dataKey, double ymu, GLMParameters glm, int rank){
//    _rank = rank;
//    _ymu = ymu;
//    _glm = glm;
//    _auc_bldr = (glm._family == Family.binomial) ? new AUC2.AUCBuilder(AUC2.NBINS) : null;
//    this.dataKey = dataKey;
//  }

//  @Override public double[] perRow(double ds[], float[] yact, Model m, double[] mean) {
//    super.perRow(ds, yact, m, mean);
//    return ds;                // Flow coding
//  }

  transient double [] _ds = new double[3];
  transient float [] _yact = new float[1];

  public void add(double yreal, double [] ymodel, double weight, double offset) {
    if(weight == 0)return;
    _yact[0] = (float) yreal;
    if(_computeMetrics)
      _metricBuilder.perRow(ymodel, _yact, weight, offset, null);
    add2(yreal, ymodel, weight, offset );
  }

  public void add(double yreal, double ymodel, double weight, double offset) {
    if(weight == 0)return;
    _yact[0] = (float) yreal;
    if(_glmf._family == Family.binomial) {
      _ds[1] = 1 - ymodel;
      _ds[2] = ymodel;
    } else {
      _ds[0] = ymodel;
    }
    if(_computeMetrics) {
      assert !(_metricBuilder instanceof MetricBuilderMultinomial):"using incorrect add call fro multinomial";
      _metricBuilder.perRow(_ds, _yact, weight, offset, null);
    }
    add2(yreal, ymodel, weight, offset );
  }

  private void add2(double yreal, double ymodel [] , double weight, double offset) {
    _wcount += weight;
    ++_nobs;
    int c = (int)yreal;
    residual_deviance -= 2 * weight * Math.log(ymodel[c+1]);
    null_devince -= 2 * weight * Math.log(_ymu[c]);
  }
  private void add2(double yreal, double ymodel, double weight, double offset) {
    _wcount += weight;
    ++_nobs;
    residual_deviance += weight * _glmf.deviance(yreal, ymodel);
    if(offset == 0)
      null_devince += weight * _glmf.deviance(yreal, _ymu[0]);
    else
      null_devince += weight * _glmf.deviance(yreal, _glmf.linkInv(offset +_glmf.link(_ymu[0])));
    if (_glmf._family == Family.poisson) { // AIC for poisson
      long y = Math.round(yreal);
      double logfactorial = MathUtils.logFactorial(y);
      _aic2 += weight * (yreal * Math.log(ymodel) - logfactorial - ymodel);
    }
  }

  public void reduce(GLMMetricBuilder v){
    if(_computeMetrics)
      _metricBuilder.reduce(v._metricBuilder);
    residual_deviance  += v.residual_deviance;
    null_devince += v.null_devince;
    _nobs += v._nobs;
    _aic2 += v._aic2;
    _wcount += v._wcount;
  }
  public final double residualDeviance() { return residual_deviance;}
  public final long nullDOF() { return _nobs - (_intercept?1:0);}
  public final long resDOF() { return _nobs - _rank;}

  protected void computeAIC(){
    _aic = 0;
    switch( _glmf._family) {
      case gaussian:
        _aic =  _nobs * (Math.log(residual_deviance / _nobs * 2 * Math.PI) + 1) + 2;
        break;
      case binomial:
        _aic = residual_deviance;
        break;
      case poisson:
        _aic = -2*_aic2;
        break; // AIC is set during the validation task
      case gamma:
        _aic = Double.NaN;
        break;
      case tweedie:
      case multinomial:
        _aic = Double.NaN;
        break;
      default:
        assert false : "missing implementation for family " + _glmf._family;
    }
    _aic += 2*_rank;
  }

//  @Override
//  public String toString(){
//    if(_metricBuilder != null)
//      return _metricBuilder.toString() + ", explained_dev = " + MathUtils.roundToNDigits(1 - residual_deviance / null_deviance,5);
//    else return "explained dev = " + MathUtils.roundToNDigits(1 - residual_deviance / null_deviance,5);
//  }

  @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
    GLMModel gm = (GLMModel)m;
    computeAIC();
    ModelMetrics metrics = _metricBuilder.makeModelMetrics(gm, f, null, null);
    if (_glmf._family == Family.binomial) {
      ModelMetricsBinomial metricsBinommial = (ModelMetricsBinomial) metrics;
      GainsLift gl = null;
      if (preds!=null) {
        Vec resp = f.vec(m._parms._response_column);
        Vec weights = f.vec(m._parms._weights_column);
        if (resp != null) {
          gl = new GainsLift(preds.lastVec(), resp, weights);
          gl.exec(m._output._job);
        }
      }
      metrics = new ModelMetricsBinomialGLM(m, f, metrics._MSE, _domain, metricsBinommial._sigma, metricsBinommial._auc, metricsBinommial._logloss, residualDeviance(), null_devince, _aic, nullDOF(), resDOF(), gl);
    } else if( _glmf._family == Family.multinomial) {
      ModelMetricsMultinomial metricsMultinomial = (ModelMetricsMultinomial) metrics;
      metrics = new ModelMetricsMultinomialGLM(m, f, metricsMultinomial._MSE, metricsMultinomial._domain, metricsMultinomial._sigma, metricsMultinomial._cm, metricsMultinomial._hit_ratios, metricsMultinomial._logloss, residualDeviance(), null_devince, _aic, nullDOF(), resDOF());
    } else {
      ModelMetricsRegression metricsRegression = (ModelMetricsRegression) metrics;
      metrics = new ModelMetricsRegressionGLM(m, f, metricsRegression._MSE, metricsRegression._sigma, residualDeviance(), residualDeviance()/_wcount, null_devince, _aic, nullDOF(), resDOF());
    }
    return gm._output.addModelMetrics(metrics); // Update the metrics in-place with the GLM version
  }

}
