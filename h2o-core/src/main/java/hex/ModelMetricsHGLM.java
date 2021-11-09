package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;

import java.util.Arrays;

public class ModelMetricsHGLM extends ModelMetricsSupervised {
  public final double[] _sefe;       // standard error of fixed predictors/effects
  public final double[] _sere;       // standard error of random effects
  public final double[] _fixef;     // fixed coefficients
  public final double[] _ranef;       // random coefficients
  public final int[] _randc;           // column indices of random columns
  public final double _varfix;       // dispersion parameter of the mean model (residual variance for LMM)
  public final double[] _varranef;     // dispersion parameter of the random effects (variance of random effects for GLMM)
  public final boolean _converge;    // true if model has converged
  public final double _dfrefe;       // deviance degrees of freedom for mean part of the model
  public final double[] _summvc1;    // estimates, standard errors of the linear predictor in the dispersion model
  public final double[][] _summvc2;// estimates, standard errors of the linear predictor for dispersion parameter of random effects
  public final double _hlik;         // log h-likelihood
  public final double _pvh;          // adjusted profile log-likelihood profiled over random effects
  public final double _pbvh;         // adjusted profile log-likelihood profiled over fixed and random effects
  public final double _caic;         // conditional AIC
  public final long  _bad;           // index of the most influential observation
  public final double _sumetadiffsquare;  // sum(etai-eta0)^2
  public final double _convergence;       // sum(etai-eta0)^2/sum(etai)^2
  public final int _iterations;
  
  public ModelMetricsHGLM(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, 
                          CustomMetric customMetric, double[] sefe, double[] sere, double varfix, double[] varranef,
                          boolean converge, double dfrefe, double[] summvc1, double[][] summvc2, double hlik, 
                          double pvh, double pbvh, double cAIC, long bad, double sumEtaDiffSq, double convergence, 
                          int[] randC, double[] fixef, double[] ranef, int iter) {
    super(model, frame, nobs, mse, domain, sigma, customMetric);
    _sefe = sefe;
    _sere = sere;
    _varfix = varfix;
    _varranef = varranef;
    _converge = converge;
    _dfrefe = dfrefe;
    _summvc1 = summvc1;
    _summvc2 = summvc2;
    _hlik = hlik;
    _pvh = pvh;
    _pbvh = pbvh;
    _caic = cAIC;
    _bad = bad;
    _sumetadiffsquare = sumEtaDiffSq;
    _convergence = convergence;
    _randc = randC;
    _fixef = fixef;
    _ranef = ranef;
    _iterations = iter;
  }

  public static ModelMetricsHGLM getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);
    if( !(mm instanceof ModelMetricsHGLM) )
      throw new H2OIllegalArgumentException("Expected to find a HGLM ModelMetrics for model: " + model._key.toString()
              + " and frame: " + frame._key.toString(), "Expected to find a ModelMetricsHGLM for model: " + 
              model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + (mm == null ? null : mm.getClass()));
    return (ModelMetricsHGLM) mm;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" standard error of fixed predictors effects: "+Arrays.toString(_sefe));
    sb.append(" standard error of random effects: "+Arrays.toString(_sere));
    sb.append(" dispersion parameter of the mean model (residual variance for LMM): "+ _varfix);
    sb.append(" dispersion parameter of the random effects (variance of random effects for GLMM): "+ _varranef);
    if (_converge)
      sb.append(" HGLM has converged.");
    else
      sb.append(" HGLM has failed to converge.");
    sb.append(" deviance degrees of freedom for mean part of the model: "+ _dfrefe);
    sb.append(" estimates, standard errors of the linear predictor in the dispersion model: "+Arrays.toString(_summvc1));
    sb.append(" estimates, standard errors of the linear predictor for dispersion parameter of random effects: "+
            Arrays.toString(_summvc2));
    sb.append(" log h-likelihood: "+_hlik);
    sb.append(" adjusted profile log-likelihood profiled over random effects: "+_pvh);
    sb.append(" adjusted profile log-likelihood profiled over fixed and random effects: "+_pbvh);
    sb.append(" conditional AIC: "+_caic);
    sb.append(" index of the most influential observation: "+_bad);
    sb.append(" sum(etai-eta0)^2: "+ _sumetadiffsquare);
    sb.append("convergence (sum(etai-eta0)^2/sum(etai)^2): "+_convergence);
    return sb.toString();
  }
  
  public static class MetricBuilderHGLM<T extends MetricBuilderHGLM<T>> extends MetricBuilderSupervised<T> {
    public double[] _sefe;       // standard error of fixed predictors/effects
    public double[] _sere;       // standard error of random effects
    public double _varfix;       // dispersion parameter of the mean model (residual variance for LMM)
    public double[] _varranef;     // dispersion parameter of the random effects (variance of random effects for GLMM)
    public boolean _converge;    // true if model has converged
    public double _dfrefe;       // deviance degrees of freedom for mean part of the model
    public double[] _summvc1;    // estimates, standard errors of the linear predictor in the dispersion model
    public double[][] _summvc2;// estimates, standard errors of the linear predictor for dispersion parameter of random effects
    public double _hlik;         // log h-likelihood
    public double _pvh;          // adjusted profile log-likelihood profiled over random effects
    public double _pbvh;         // adjusted profile log-likelihood profiled over fixed and random effects
    public double _caic;         // conditional AIC
    public long  _bad;           // index of the most influential observation
    public double _sumetadiffsquare;  // sum(etai-eta0)^2
    public double _convergence;       // sum(etai-eta0)^2/sum(etai)^2
    public double[] _fixef;
    public double[] _ranef;
    public int[] _randc;
    public int _iterations;    // number of iterations
    public long _nobs;

    public MetricBuilderHGLM(String[] domain) {
      super(0,domain);
    }
    
    public void updateCoeffs(double[] fixedCoeffs, double[] randCoeffs) {
      int fixfLen = fixedCoeffs.length;
      if (_fixef ==null) 
        _fixef = new double[fixfLen];
      System.arraycopy(fixedCoeffs, 0, _fixef, 0, fixfLen);
      
      int randLen = randCoeffs.length;
      if (_ranef == null)
        _ranef = new double[randLen];
      System.arraycopy(randCoeffs, 0, _ranef, 0, randLen);
      
    }

    public void updateSummVC(double[] VC1, double[][] VC2, int[] randc) {
      if (_summvc1 ==null)
        _summvc1 = new double[2];
      System.arraycopy(VC1, 0, _summvc1, 0, 2);
      
      if (_summvc2 == null) {
        _randc = randc;
        _summvc2 = new double[randc.length][2];
      }
      
      for (int index = 0; index < randc.length; index++)
        System.arraycopy(VC2[index], 0, _summvc2[index], 0, 2);
    }
    
    @Override
    public double[] perRow(double[] ds, float[] yact, Model m) {
      return new double[0];
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      ModelMetricsHGLM mm = new ModelMetricsHGLM(m, f, _nobs, 0, _domain, 0, _customMetric, _sefe, _sere,
              _varfix, _varranef, _converge, _dfrefe, _summvc1, _summvc2, _hlik, _pvh, _pbvh, _caic, _bad,
              _sumetadiffsquare, _convergence, _randc, _fixef, _ranef, _iterations);
      if (m!=null) m.addModelMetrics(mm);
      return mm;
    }
  }

  public static class IndependentMetricBuilderHGLM<T extends IndependentMetricBuilderHGLM<T>> extends IndependentMetricBuilderSupervised<T> {
    public double[] _sefe;       // standard error of fixed predictors/effects
    public double[] _sere;       // standard error of random effects
    public double _varfix;       // dispersion parameter of the mean model (residual variance for LMM)
    public double[] _varranef;     // dispersion parameter of the random effects (variance of random effects for GLMM)
    public boolean _converge;    // true if model has converged
    public double _dfrefe;       // deviance degrees of freedom for mean part of the model
    public double[] _summvc1;    // estimates, standard errors of the linear predictor in the dispersion model
    public double[][] _summvc2;// estimates, standard errors of the linear predictor for dispersion parameter of random effects
    public double _hlik;         // log h-likelihood
    public double _pvh;          // adjusted profile log-likelihood profiled over random effects
    public double _pbvh;         // adjusted profile log-likelihood profiled over fixed and random effects
    public double _caic;         // conditional AIC
    public long  _bad;           // index of the most influential observation
    public double _sumetadiffsquare;  // sum(etai-eta0)^2
    public double _convergence;       // sum(etai-eta0)^2/sum(etai)^2
    public double[] _fixef;
    public double[] _ranef;
    public int[] _randc;
    public int _iterations;    // number of iterations
    public long _nobs;

    public IndependentMetricBuilderHGLM(String[] domain) {
      super(0,domain);
    }

    public void updateCoeffs(double[] fixedCoeffs, double[] randCoeffs) {
      int fixfLen = fixedCoeffs.length;
      if (_fixef ==null)
        _fixef = new double[fixfLen];
      System.arraycopy(fixedCoeffs, 0, _fixef, 0, fixfLen);

      int randLen = randCoeffs.length;
      if (_ranef == null)
        _ranef = new double[randLen];
      System.arraycopy(randCoeffs, 0, _ranef, 0, randLen);

    }

    public void updateSummVC(double[] VC1, double[][] VC2, int[] randc) {
      if (_summvc1 ==null)
        _summvc1 = new double[2];
      System.arraycopy(VC1, 0, _summvc1, 0, 2);

      if (_summvc2 == null) {
        _randc = randc;
        _summvc2 = new double[randc.length][2];
      }

      for (int index = 0; index < randc.length; index++)
        System.arraycopy(VC2[index], 0, _summvc2[index], 0, 2);
    }

    @Override
    public double[] perRow(double[] ds, float[] yact) {
      return new double[0];
    }

    @Override
    public ModelMetrics makeModelMetrics() {
      ModelMetricsHGLM mm = new ModelMetricsHGLM(null, null, _nobs, 0, _domain, 0, _customMetric, _sefe, _sere,
              _varfix, _varranef, _converge, _dfrefe, _summvc1, _summvc2, _hlik, _pvh, _pbvh, _caic, _bad,
              _sumetadiffsquare, _convergence, _randc, _fixef, _ranef, _iterations);
      return mm;
    }
  }
}
