package hex.glm;

import hex.*;
import hex.ModelMetrics.MetricBuilder;
import hex.ModelMetricsBinomial.MetricBuilderBinomial;
import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.ModelMetricsBinomialGLM.ModelMetricsOrdinalGLM;
import hex.ModelMetricsHGLM.MetricBuilderHGLM;
import hex.ModelMetricsMultinomial.MetricBuilderMultinomial;
import hex.ModelMetricsOrdinal.MetricBuilderOrdinal;
import hex.ModelMetricsRegression.MetricBuilderRegression;
import hex.ModelMetricsSupervised.MetricBuilderSupervised;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMWeightsFun;
import water.H2O;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;

;

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
  double _log_likelihood;
  double _aic;// internal AIC used only for poisson family!
  private double _aic2;// internal AIC used only for poisson family!
  final GLMModel.GLMWeightsFun _glmf;
  final private int _rank;
  MetricBuilder _metricBuilder;
  final boolean _intercept;
  private final double [] _ymu;
  final boolean _computeMetrics;
  public GLMMetricBuilder(String[] domain, double [] ymu, GLMWeightsFun glmf, int rank, boolean computeMetrics, boolean intercept){
    super(domain == null?0:domain.length, domain);
    _glmf = glmf;
    _rank = rank;
    _computeMetrics = computeMetrics;
    _intercept = intercept;
    _ymu = ymu;
    if(_computeMetrics) {
      if (domain!=null && domain.length==1 && domain[0].contains("HGLM")) {
        _metricBuilder = new MetricBuilderHGLM(domain);
      } else {
        switch (_glmf._family) {
          case binomial:
          case quasibinomial:
          case fractionalbinomial:
            _metricBuilder = new MetricBuilderBinomial(domain);
            break;
          case multinomial:
            _metricBuilder = new MetricBuilderMultinomial(domain.length, domain);
            ((MetricBuilderMultinomial) _metricBuilder)._priorDistribution = ymu;
            break;
          case ordinal:
            _metricBuilder = new MetricBuilderOrdinal(domain.length, domain);
            ((MetricBuilderOrdinal) _metricBuilder)._priorDistribution = ymu;
            break;
          default:
            _metricBuilder = new MetricBuilderRegression();
            break;
        }
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
    if (_glmf._family.equals(Family.negativebinomial))
      _log_likelihood += m.likelihood(weight, yact[0], ds[0]);
    if(!ArrayUtils.hasNaNsOrInfs(ds) && !ArrayUtils.hasNaNsOrInfs(yact)) {
      if(_glmf._family == Family.multinomial || _glmf._family == Family.ordinal)
        add2(yact[0], ds, weight, offset);
      else if (_glmf._family == Family.binomial || _glmf._family == Family.quasibinomial ||
              _glmf._family.equals(Family.fractionalbinomial))
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
    if(_glmf._family == Family.binomial || _glmf._family == Family.quasibinomial) {
      _ds[1] = 1 - ymodel;
      _ds[2] = ymodel;
    } else {
      _ds[0] = ymodel;
    }
    if(_computeMetrics) {
      assert (!(_metricBuilder instanceof MetricBuilderMultinomial) &&
              !(_metricBuilder instanceof MetricBuilderOrdinal)):"using incorrect add call for multinomial/ordinal";
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
    if (_glmf._family.equals(Family.negativebinomial))
      _log_likelihood += v._log_likelihood;
    _nobs += v._nobs;
    _aic2 += v._aic2;
    _wcount += v._wcount;
  }
  public final double residualDeviance() { return residual_deviance;}
  public final long nullDOF() { return _nobs - (_intercept?1:0);}
  public final long resDOF() {
    if (_glmf._family == Family.ordinal)  // rank counts all non-zero multinomial coeffs: nclasses-1 sets of non-zero coeffss
      return _nobs-(_rank/(_nclasses-1)+_nclasses-2); // rank/nclasses-1 represent one beta plus one intercept.  Need nclasses-2 more intercepts.
    else
      return _nobs - _rank;
  }

  protected void computeAIC(){
    _aic = 0;
    switch( _glmf._family) {
      case gaussian:
        _aic =  _nobs * (Math.log(residual_deviance / _nobs * 2 * Math.PI) + 1) + 2;
        break;
      case quasibinomial:
      case binomial:
      case fractionalbinomial:
        _aic = residual_deviance;
        break;
      case poisson:
        _aic = -2*_aic2;
        break; // AIC is set during the validation task
      case gamma:
        _aic = Double.NaN;
        break;
      case ordinal:
      case tweedie:
      case multinomial:
        _aic = Double.NaN;
        break;
      case negativebinomial:
        _aic = 2* _log_likelihood;
        break;
      default:
        assert false : "missing implementation for family " + _glmf._family;
    }
    _aic += 2*_rank;
  }

  @Override public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
    GLMModel gm = (GLMModel) m;
    if (!gm._parms._HGLM)
      computeAIC();
    ModelMetrics metrics = _metricBuilder.makeModelMetrics(gm, f, null, null);
    if (gm._parms._HGLM) { // HGLM 
      ModelMetricsHGLM.MetricBuilderHGLM metricsBDHGLM = (ModelMetricsHGLM.MetricBuilderHGLM) _metricBuilder;
      metrics = new ModelMetricsHGLMGaussianGaussian(m, f, metricsBDHGLM._nobs, 0,
              ((ModelMetricsHGLM) metrics)._domain, 0, 
              metrics._custom_metric, metricsBDHGLM._sefe, metricsBDHGLM._sere, metricsBDHGLM._varfix, 
              metricsBDHGLM._varranef, metricsBDHGLM._converge,metricsBDHGLM._dfrefe, metricsBDHGLM._summvc1, 
              metricsBDHGLM._summvc2,metricsBDHGLM._hlik, metricsBDHGLM._pvh, metricsBDHGLM._pbvh, metricsBDHGLM._caic, 
              metricsBDHGLM._bad, metricsBDHGLM._sumetadiffsquare, metricsBDHGLM._convergence, metricsBDHGLM._randc, 
              metricsBDHGLM._fixef, metricsBDHGLM._ranef, metricsBDHGLM._iterations);
    } else {
      if (_glmf._family == Family.binomial || _glmf._family == Family.quasibinomial || 
              _glmf._family == Family.fractionalbinomial) {
        ModelMetricsBinomial metricsBinommial = (ModelMetricsBinomial) metrics;
        GainsLift gl = null;
        if (preds != null) {
          Vec resp = f.vec(m._parms._response_column);
          Vec weights = f.vec(m._parms._weights_column);
          if (resp != null && !_glmf._family.equals(Family.fractionalbinomial)) { // don't calculate for frac binomial
            gl = new GainsLift(preds.lastVec(), resp, weights);
            gl.exec(m._output._job);
          }
        }
        metrics = new ModelMetricsBinomialGLM(m, f, metrics._nobs, metrics._MSE, _domain, metricsBinommial._sigma, metricsBinommial._auc, metricsBinommial._logloss, residualDeviance(), null_devince, _aic, nullDOF(), resDOF(), gl, _customMetric);
      } else if (_glmf._family == Family.multinomial) {
        ModelMetricsMultinomial metricsMultinomial = (ModelMetricsMultinomial) metrics;
        metrics = new ModelMetricsMultinomialGLM(m, f, metricsMultinomial._nobs, metricsMultinomial._MSE, metricsMultinomial._domain, metricsMultinomial._sigma, metricsMultinomial._cm, metricsMultinomial._hit_ratios, metricsMultinomial._logloss, residualDeviance(), null_devince, _aic, nullDOF(), resDOF(), _customMetric);
      } else if (_glmf._family == Family.ordinal) { // ordinal should have a different resDOF()
        ModelMetricsOrdinal metricsOrdinal = (ModelMetricsOrdinal) metrics;
        metrics = new ModelMetricsOrdinalGLM(m, f, metricsOrdinal._nobs, metricsOrdinal._MSE, metricsOrdinal._domain, metricsOrdinal._sigma, metricsOrdinal._cm, metricsOrdinal._hit_ratios, metricsOrdinal._logloss, residualDeviance(), null_devince, _aic, nullDOF(), resDOF(), _customMetric);
      } else {
        ModelMetricsRegression metricsRegression = (ModelMetricsRegression) metrics;
        metrics = new ModelMetricsRegressionGLM(m, f, metricsRegression._nobs, metricsRegression._MSE, metricsRegression._sigma, metricsRegression._mean_absolute_error, metricsRegression._root_mean_squared_log_error, residualDeviance(), residualDeviance() / _wcount, null_devince, _aic, nullDOF(), resDOF(), _customMetric);
      }
    }
    return gm.addModelMetrics(metrics); // Update the metrics in-place with the GLM version, do DKV.put
  }

}
