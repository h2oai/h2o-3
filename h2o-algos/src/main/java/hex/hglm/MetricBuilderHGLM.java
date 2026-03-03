package hex.hglm;

import Jama.Matrix;
import hex.*;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.List;

import static hex.glm.GLMModel.GLMParameters.MissingValuesHandling.*;

public class MetricBuilderHGLM extends ModelMetricsSupervised.MetricBuilderSupervised<MetricBuilderHGLM> {
  // the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
  // I will be referring to the doc and different parts of it to explain my implementation.
  public static final double LOG_2PI = Math.log(2*Math.PI);
  ModelMetrics.MetricBuilder _metricBuilder;  // point to generic model metric classes
  final boolean _intercept;
  final boolean _random_intercept;
  final boolean _computeMetrics;
  public double[] _beta;
  public double[][] _ubeta;
  public double[][] _tmat;
  public double _yMinusFixPredSquare;
  public double _sse;
  public int _nobs;
  
  public MetricBuilderHGLM(String[] domain, boolean computeMetrics, boolean intercept, boolean random_intercept, 
                           HGLMModel.HGLMModelOutput output) {
    super(domain == null ? 0 : domain.length, domain);
    _intercept = intercept;
    _computeMetrics = computeMetrics;
    _random_intercept = random_intercept;
    _metricBuilder = new ModelMetricsRegression.MetricBuilderRegression(); // everything else goes back regression
    _beta = output._beta;
    _ubeta = output._ubeta;
    _tmat = output._tmat;
  }
  
  public double[] perRow(double[] ds, float[] yact, double weight, double offset, double[] xji, double[] zji, 
                         double[][] yMinusXTimesZ, int level2Index, Model m) {
    if (weight == 0) return ds;
    _metricBuilder.perRow(ds, yact, weight, offset, m);
    add2(yact[0], ds[0], weight, xji, zji, yMinusXTimesZ, level2Index, offset);
    return ds;
  }
  
  private void add2(double yresp, double predictedVal, double weight, double[] input, double[] randomInput, 
                    double[][] yMinusXTimesZ, int level2Index, double offset) {
    double temp = yresp- ArrayUtils.innerProduct(_beta, input)-offset;
    _yMinusFixPredSquare += temp*temp;
    ArrayUtils.add(yMinusXTimesZ[level2Index], ArrayUtils.mult(randomInput, temp));
    _nobs++;
    temp = yresp-predictedVal;
    _sse += temp*temp;
  }
  
  @Override
  public void reduce(MetricBuilderHGLM other) {
    _metricBuilder.reduce(other._metricBuilder);
    _yMinusFixPredSquare += other._yMinusFixPredSquare;
    _sse += other._sse;
    _nobs += other._nobs;
  }

  @Override
  public double[] perRow(double[] ds, float[] yact, Model m) {
    return ds;
  }

  @Override
  public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
    HGLMModel hglmM = (HGLMModel) m;
    ModelMetrics mm = _metricBuilder.makeModelMetrics(hglmM, f, null, null);
    ModelMetricsRegression metricsRegression = (ModelMetricsRegression) mm;
    boolean forTraining = m._parms.train().getKey().equals(f.getKey());
    double[][] tmat = hglmM._output._tmat; // already set with non-standardized random coefficients

    if (forTraining) {
      double loglikelihood = calHGLMLlg(metricsRegression._nobs, tmat, hglmM._output._tau_e_var, hglmM._output._arjtarj,
              this._yMinusFixPredSquare, hglmM._output._yMinusXTimesZ);
      mm = new ModelMetricsRegressionHGLM(m, f, metricsRegression._nobs, this.weightedSigma(), loglikelihood,
              this._customMetric, hglmM._output._iterations, hglmM._output._beta, hglmM._output._ubeta,
              tmat, hglmM._output._tau_e_var, metricsRegression._MSE, this._yMinusFixPredSquare / metricsRegression._nobs,
              metricsRegression.mae(), metricsRegression._root_mean_squared_log_error,
              metricsRegression._mean_residual_deviance, metricsRegression.aic());
    } else {
      List<String> colNames = Arrays.asList(f.names());
      boolean hasWeights = hglmM._parms._weights_column != null && colNames.contains(hglmM._parms._weights_column);
      boolean hasOffsets = hglmM._parms._offset_column != null && colNames.contains(hglmM._parms._offset_column);
      DataInfo dinfo = new DataInfo(adaptedFrame, null, 1, hglmM._parms._use_all_factor_levels,
              DataInfo.TransformType.NONE, DataInfo.TransformType.NONE,
              hglmM._parms.missingValuesHandling() == Skip,
              hglmM._parms.missingValuesHandling() == MeanImputation
                      || hglmM._parms.missingValuesHandling() == PlugValues,
              hglmM._parms.makeImputer(), false, hasWeights, hasOffsets, false, null);
      HGLMTask.ComputationEngineTask engineTask = new HGLMTask.ComputationEngineTask(null, hglmM._parms, dinfo);
      engineTask.doAll(dinfo._adaptedFrame);
      double loglikelihood = calHGLMLlg(engineTask._nobs, tmat, hglmM._output._tau_e_var, engineTask._ArjTArj,
              this._yMinusFixPredSquare, hglmM._output._yMinusXTimesZValid);
      mm = new ModelMetricsRegressionHGLM(m, f, metricsRegression._nobs, this.weightedSigma(), loglikelihood,
              this._customMetric, hglmM._output._iterations, hglmM._output._beta, hglmM._output._ubeta, tmat,
              hglmM._output._tau_e_var,metricsRegression._MSE, this._yMinusFixPredSquare /metricsRegression._nobs,
              metricsRegression.mae(), metricsRegression._root_mean_squared_log_error, 
              metricsRegression._mean_residual_deviance, metricsRegression.aic());
      hglmM._output._nobs_valid = engineTask._nobs;
    }

    if (m != null)
      m.addModelMetrics(mm);
    return mm;
  }

  /**
   *
   * This method calculates the log-likelihood as described in section II.V of the doc.
   */
  public static double calHGLMLlg(long nobs, double[][] tmat, double varResidual, double[][][] zjTTimesZj,
                                  double yMinsXFixSquared, double[][] yMinusXFixTimesZ) {
    int numLevel2 = zjTTimesZj.length;
    double[][] tmatInv = new Matrix(tmat).inverse().getArray();
    double tmatDeterminant = new Matrix(tmat).det();
    double oneOVar = 1.0 / varResidual;
    double oneOVarSq = oneOVar * oneOVar;
    double llg = nobs * LOG_2PI + oneOVar * yMinsXFixSquared;
    double[][] invTPlusZjTZ;
    Matrix yMinusXjFixed;
    Matrix yjMinusXjFixed;
    for (int ind2 = 0; ind2 < numLevel2; ind2++) {
      invTPlusZjTZ = calInvTPZjTZ(tmatInv, zjTTimesZj[ind2], oneOVar);
      llg += Math.log(varResidual * new Matrix(invTPlusZjTZ).det() * tmatDeterminant);
      yMinusXjFixed = new Matrix(new double[][]{yMinusXFixTimesZ[ind2]});
      yjMinusXjFixed = yMinusXjFixed.times(new Matrix(invTPlusZjTZ).inverse().times(yMinusXjFixed.transpose()));
      llg -= oneOVarSq * yjMinusXjFixed.getArray()[0][0];
    }
    return -0.5 * llg;
  }

  public static double[][] calInvTPZjTZ(double[][] tmatInv, double[][] zjTTimesZj, double oneOVar) {
    return new Matrix(tmatInv).plus(new Matrix(zjTTimesZj).times(oneOVar)).getArray();
  }
}
