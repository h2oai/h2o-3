package hex.glm;

import hex.*;
import hex.ModelMetrics.MetricBuilder;
import hex.ModelMetricsBinomial.MetricBuilderBinomial;
import hex.ModelMetricsRegression.MetricBuilderRegression;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import water.DKV;
import water.Iced;
import water.Key;
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
  final double [] _ymu;
  double residual_deviance;
  double null_deviance;
  long nobs;
  double aic;// internal AIC used only for poisson family!
  private double _aic2;// internal AIC used only for poisson family!
  final GLMModel.GLMParameters _glm;
  final private int _rank;
  final double _threshold;
  AUC2 _auc2;
  MetricBuilder _metricBuilder;
  boolean _intercept = true;
  final boolean _computeMetrics;
  public GLMValidation(String[] domain, double ymu, GLMParameters glm, int rank, double threshold, boolean computeMetrics){
    super(domain);
    _rank = rank;
    _ymu = new double[]{ymu};
    _glm = glm;
    _threshold = threshold;
    _computeMetrics = computeMetrics;
    if(_computeMetrics)
      _metricBuilder = _glm._family == Family.binomial
        ?new MetricBuilderBinomial(domain)
        :new MetricBuilderRegression();
  }



  public double explainedDev(){
    return 1.0 - residualDeviance()/nullDeviance();
  }


  @Override public double[] perRow(double ds[], float[] yact, Model m) {
    _metricBuilder.perRow(ds,yact,m);
    if(!ArrayUtils.hasNaNsOrInfs(ds) && !ArrayUtils.hasNaNsOrInfs(yact)) {
      if (_glm._family == Family.binomial)
        add2(yact[0], ds[2]);
      else
        add2(yact[0], ds[0]);
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


  public void add(double yreal, double ymodel) {
    _yact[0] = (float) yreal;
    if(_glm._family == Family.binomial) {
      _ds[0] = ymodel > _threshold ? 1 : 0;
      _ds[1] = 1 - ymodel;
      _ds[2] = ymodel;
    } else {
      _ds[0] = ymodel;
    }
    if(_computeMetrics)
      _metricBuilder.perRow(_ds, _yact, null);
    add2(yreal, ymodel);
  }
  private void add2(double yreal, double ymodel) {
    null_deviance += _glm.deviance(yreal, _ymu[0]);
    residual_deviance  += _glm.deviance(yreal, ymodel);
    ++nobs;
    if( _glm._family == Family.poisson ) { // AIC for poisson
      long y = Math.round(yreal);
      double logfactorial = 0;
      for( long i = 2; i <= y; ++i )
        logfactorial += Math.log(i);
      _aic2 += (yreal * Math.log(ymodel) - logfactorial - ymodel);
    }
  }

  public void reduce(GLMValidation v){
    if(_computeMetrics)
      _metricBuilder.reduce(v._metricBuilder);
    residual_deviance  += v.residual_deviance;
    null_deviance += v.null_deviance;
    nobs += v.nobs;
    _aic2 += v._aic2;
  }
  public final double nullDeviance(){return null_deviance;}
  public final double residualDeviance(){return residual_deviance;}
  public final long nullDOF(){return nobs - (_intercept?1:0);}
  public final long resDOF(){return nobs - _rank;}

  protected double computeAUC(){
    if(_glm._family != Family.binomial)
      throw new IllegalArgumentException("AUC only defined for family == 'binomial', got '" + _glm._family + "'");
    return ((MetricBuilderBinomial)_metricBuilder).auc();
  }
  public double bestThreshold(){ return _auc2==null ? Double.NaN : _auc2.defaultThreshold();}
//  public double AIC(){return aic;}
  protected void computeAIC(){
    aic = 0;
    switch( _glm._family) {
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
//      case tweedie:
//        aic = Double.NaN;
//        break;
      default:
        assert false : "missing implementation for family " + _glm._family;
    }
    aic += 2*_rank;
  }

  private transient ModelMetrics _metrics;

  @Override
  public String toString(){
    if(_metricBuilder != null)
      return _metricBuilder.toString() + ", explained_dev = " + MathUtils.roundToNDigits(1 - residual_deviance / null_deviance,5);
    else return "explained dev = " + MathUtils.roundToNDigits(1 - residual_deviance / null_deviance,5);
  }

  @Override public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma) {
    GLMModel gm = (GLMModel)m;
    computeAIC();
    ModelMetrics metrics = _metrics == null?_metricBuilder.makeModelMetrics(m, f, sigma):_metrics;
    if (_glm._family == Family.binomial) {
      ModelMetricsBinomial metricsBinommial = (ModelMetricsBinomial) metrics;
      metrics = new ModelMetricsBinomialGLM(m, f, metrics._MSE, _domain, metricsBinommial._sigma, metricsBinommial._auc, metricsBinommial._logloss, residualDeviance(), nullDeviance(), aic, nullDOF(), resDOF());
    } else {
      ModelMetricsRegression metricsRegression = (ModelMetricsRegression) metrics;
      metrics = new ModelMetricsRegressionGLM(m, f, metricsRegression._MSE, metricsRegression._sigma, residualDeviance(), nullDeviance(), aic, nullDOF(), resDOF());
    }
    DKV.put(metrics._key,metrics);
    return gm._output.addModelMetrics(metrics);
  }
}
