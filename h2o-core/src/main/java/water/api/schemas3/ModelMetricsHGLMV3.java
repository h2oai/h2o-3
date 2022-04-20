package water.api.schemas3;

import hex.ModelMetricsHGLM;
import water.api.API;

public class ModelMetricsHGLMV3<I extends ModelMetricsHGLM, S extends ModelMetricsHGLMV3<I, S>>
        extends ModelMetricsBaseV3<I, S> {
  @API(help="standard error of fixed predictors/effects", direction=API.Direction.OUTPUT)
  public double[] sefe;       // standard error of fixed predictors/effects

  @API(help="standard error of random effects", direction=API.Direction.OUTPUT)  
  public double[] sere;       // standard error of random effects

  @API(help="dispersion parameter of the mean model (residual variance for LMM)", direction=API.Direction.OUTPUT)
  public double varfix;       // dispersion parameter of the mean model (residual variance for LMM)

  @API(help="dispersion parameter of the random effects (variance of random effects for GLMM", direction=API.Direction.OUTPUT)
  public double[] varranef;     // dispersion parameter of the random effects (variance of random effects for GLMM)

  @API(help="fixed coefficient)", direction=API.Direction.OUTPUT)
  public double[] fixef;       // dispersion parameter of the mean model (residual variance for LMM)

  @API(help="random coefficients", direction=API.Direction.OUTPUT)
  public double[] ranef;     // dispersion parameter of the random effects (variance of random effects for GLMM)
 
  @API(help="true if model has converged", direction=API.Direction.OUTPUT)
  public boolean converge;    // true if model has converged

  @API(help="number of random columns", direction=API.Direction.OUTPUT)
  public int[] randc;       // indices of random columns

  @API(help="deviance degrees of freedom for mean part of the model", direction=API.Direction.OUTPUT)
  public double dfrefe;       // deviance degrees of freedom for mean part of the model

  @API(help="estimates, standard errors of the linear predictor in the dispersion model", direction=API.Direction.OUTPUT)  
  public double[] summvc1;    // estimates, standard errors of the linear predictor in the dispersion model

  @API(help="estimates, standard errors of the linear predictor for dispersion parameter of random effects", direction=API.Direction.OUTPUT)
  public double[][] summvc2;// estimates, standard errors of the linear predictor for dispersion parameter of random effects

  @API(help="log h-likelihood", direction=API.Direction.OUTPUT)
  public double hlik;         // log h-likelihood

  @API(help="adjusted profile log-likelihood profiled over random effects", direction=API.Direction.OUTPUT)  
  public double pvh;          // adjusted profile log-likelihood profiled over random effects

  @API(help="adjusted profile log-likelihood profiled over fixed and random effects", direction=API.Direction.OUTPUT)
  public double pbvh;         // adjusted profile log-likelihood profiled over fixed and random effects

  @API(help="conditional AIC", direction=API.Direction.OUTPUT)
  public double caic;         // conditional AIC
  
  @API(help="index of the most influential observation", direction=API.Direction.OUTPUT)
  public long  bad;           // index of the most influential observation
  
  @API(help="sum(etai-eta0)^2 where etai is current eta and eta0 is the previous one", direction=API.Direction.OUTPUT)
  public double sumetadiffsquare;  // sum(etai-eta0)^2
  
  @API(help="sum(etai-eta0)^2/sum(etai)^2 ", direction=API.Direction.OUTPUT)
  public double convergence;       // sum(etai-eta0)^2/sum(etai)^2
  
  @Override
  public S fillFromImpl(ModelMetricsHGLM modelMetrics) {
    super.fillFromImpl(modelMetrics);
    hlik = modelMetrics._hlik;
    pvh = modelMetrics._pvh;
    pbvh = modelMetrics._pbvh;
    caic = modelMetrics._caic;
    bad = modelMetrics._bad;
    sumetadiffsquare =modelMetrics._sumetadiffsquare;
    convergence = modelMetrics._convergence;
    randc = modelMetrics._randc;
    fixef = modelMetrics._fixef;
    ranef = modelMetrics._ranef;
    varfix = modelMetrics._varfix;
    varranef = modelMetrics._varranef;
    converge = modelMetrics._converge;
    dfrefe = modelMetrics._dfrefe;
    sefe = copyRandEff(modelMetrics._sefe);
    sere = copyRandEff(modelMetrics._sere);
    varranef = copyRandEff(modelMetrics._varranef);
    summvc1 = copyRandEff(modelMetrics._summvc1);
    if (randc != null) {
      int numRandCol = randc.length;
      summvc2 = new double[numRandCol][];
      for (int index = 0; index < numRandCol; index++)
        summvc2[index] = copyRandEff(modelMetrics._summvc2[index]);
    }
    
    return (S) this;
  }
  
  public static double[] copyRandEff(double[] source) {
    if (source != null) {
      int sourceLen = source.length;
      double[] target = new double[sourceLen];
      System.arraycopy(source, 0, target, 0, sourceLen);
      return target;
    } else {
      return null;
    }
  }
}
