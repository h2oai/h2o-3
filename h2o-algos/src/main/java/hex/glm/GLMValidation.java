package hex.glm;

import hex.*;
import hex.ModelMetrics.MetricBuilder;
import hex.ModelMetricsBinomial.MetricBuilderBinomial;
import hex.ModelMetricsRegression.MetricBuilderRegression;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import water.DKV;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.MathUtils;

/**
 * Class for GLMValidation.
 *
 * @author tomasnykodym
 *
 */
public class GLMValidation extends MetricBuilderBinomial<GLMValidation> {
  double residual_deviance;
  double null_deviance;
  final double _ymu;
  final double _ymuLink;
  long nobs;
  double aic;// internal AIC used only for poisson family!
  private double _aic2;// internal AIC used only for poisson family!
  final GLMModel.GLMParameters _parms;
  final private int _rank;
  final double _threshold;
  MetricBuilder _metricBuilder;
  boolean _intercept = true;

  final boolean _computeMetrics;
  public GLMValidation(String[] domain, boolean intercept, double ymu, GLMParameters parms, int rank, double threshold, boolean computeMetrics){
    super(domain);
    _intercept = intercept;
    _rank = rank;
    _parms = parms;
    _threshold = threshold;
    _computeMetrics = computeMetrics;
    _ymu = parms._intercept?ymu:parms._family == Family.binomial?.5:0;
    _ymuLink = _parms.link(ymu);
    if(_computeMetrics)
      _metricBuilder = _parms._family == Family.binomial
        ?new MetricBuilderBinomial(domain)
        :new MetricBuilderRegression();
  }

  public double explainedDev(){
    return 1.0 - residualDeviance()/nullDeviance();
  }

  @Override public double[] perRow(double ds[], float[] yact, Model m) {
    return perRow(ds, yact, 1, 0, m);
  }

  @Override public double[] perRow(double ds[], float[] yact, double weight, double offset, Model m) {
    if(weight == 0)return ds;
    _metricBuilder.perRow(ds,yact,weight,offset,m);
    if(!ArrayUtils.hasNaNsOrInfs(ds) && !ArrayUtils.hasNaNsOrInfs(yact)) {
      if (_parms._family == Family.binomial)
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


  public void add(double yreal, double ymodel, double weight, double offset) {
    if(weight == 0)return;
    _yact[0] = (float) yreal;
    if(_parms._family == Family.binomial) {
      _ds[0] = ymodel > _threshold ? 1 : 0;
      _ds[1] = 1 - ymodel;
      _ds[2] = ymodel;
    } else {
      _ds[0] = ymodel;
    }
    if(_computeMetrics)
      _metricBuilder.perRow(_ds, _yact, weight, offset, null);
    add2(yreal, ymodel, weight, offset );
  }

  private void add2(double yreal, double ymodel, double weight, double offset) {
    _wcount += weight;
    residual_deviance  += weight* _parms.deviance(yreal, ymodel);
    double ynull = offset == 0?_ymu: _parms.linkInv(offset + _ymuLink /* Note: _ymuLink in this case is expected to be link(c), where c is constant term of a model fitted with the given offset and no predictors */);
    null_deviance += weight* _parms.deviance(yreal,ynull);
    ++nobs;
    if( _parms._family == Family.poisson ) { // AIC for poisson
      long y = Math.round(yreal);
      double logfactorial = 0;
      for( long i = 2; i <= y; ++i )
        logfactorial += Math.log(i);
      _aic2 += weight * (yreal * Math.log(ymodel) - logfactorial - ymodel);
    }
  }

  public void reduce(GLMValidation v){
    if(_computeMetrics)
      _metricBuilder.reduce(v._metricBuilder);
    residual_deviance  += v.residual_deviance;
    null_deviance += v.null_deviance;
    nobs += v.nobs;
    _aic2 += v._aic2;
    _wcount += v._wcount;
  }
  public final double nullDeviance() { return null_deviance;}
  public final double residualDeviance() { return residual_deviance;}
  public final long nullDOF() { return nobs - (_intercept?1:0);}
  public final long resDOF() { return nobs - _rank;}

  protected void computeAIC(){
    aic = 0;
    switch( _parms._family) {
      case gaussian:
        aic =  nobs * (Math.log(residual_deviance / nobs * 2 * Math.PI) + 1) + 2;
        break;
      case binomial:
        aic = residual_deviance;
        break;
      case poisson:
        aic = -2*_aic2;
        break; // AIC is set during the validation task
      case gamma:
        aic = Double.NaN;
        break;
      case tweedie:
        aic = Double.NaN;
        break;
      default:
        assert false : "missing implementation for family " + _parms._family;
    }
    aic += 2*_rank;
  }

  @Override
  public String toString(){
    if(_metricBuilder != null)
      return _metricBuilder.toString() + ", explained_dev = " + MathUtils.roundToNDigits(1 - residual_deviance / null_deviance,5);
    else return "explained dev = " + MathUtils.roundToNDigits(1 - residual_deviance / null_deviance,5);
  }

  @Override public ModelMetrics makeModelMetrics( Model m, Frame f) {
    GLMModel gm = (GLMModel)m;
    computeAIC();
    ModelMetrics metrics = _metricBuilder.makeModelMetrics(gm, f);
    if (_parms._family == Family.binomial) {
      ModelMetricsBinomial metricsBinomial = (ModelMetricsBinomial) metrics;
      metrics = new ModelMetricsBinomialGLM(gm, f, metrics._MSE, _domain, metricsBinomial._sigma, metricsBinomial._auc, metricsBinomial._logloss, residualDeviance(), nullDeviance(), aic, nullDOF(), resDOF());
    } else {
      ModelMetricsRegression metricsRegression = (ModelMetricsRegression) metrics;
      metrics = new ModelMetricsRegressionGLM(gm, f, metricsRegression._MSE, metricsRegression._sigma, residualDeviance(), residualDeviance()/_wcount, nullDeviance(), aic, nullDOF(), resDOF());
    }
    return gm._output.addModelMetrics(metrics); // Update the metrics in-place with the GLM version
  }
}
