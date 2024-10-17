package hex.hglm;

import hex.*;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.List;

import static hex.glm.GLMModel.GLMParameters.MissingValuesHandling.*;

public class MetricBuilderHGLM extends ModelMetricsSupervised.MetricBuilderSupervised<MetricBuilderHGLM> {
  // the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
  // I will be referring to the doc and different parts of it to explain my implementation.
  ModelMetrics.MetricBuilder _metricBuilder;  // point to generic model metric classes
  final boolean _intercept;
  final boolean _random_intercept;
  final boolean _computeMetrics;
  public double[] _beta;
  public double[][] _ubeta;
  public double[][] _tmat;
  public double _yMinusfixPredSquare;
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
    _yMinusfixPredSquare += temp*temp;
    ArrayUtils.add(yMinusXTimesZ[level2Index], ArrayUtils.mult(randomInput, temp));
    _nobs++;
    temp = yresp-predictedVal;
    _sse += temp*temp;
  }
  
  @Override
  public void reduce(MetricBuilderHGLM other) {
    _metricBuilder.reduce(other._metricBuilder);
    _yMinusfixPredSquare += other._yMinusfixPredSquare;
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
    ModelMetrics mm;
    boolean forTraining = m._parms.train().getKey().equals(f.getKey());
    double[][] tmat = hglmM._output._tmat; // already set with non-standardized random coefficients
    double mse = this._sse / hglmM._output._nobs;
    if (forTraining) {
      mm = new ModelMetricsRegressionHGLM(m, f, hglmM._output._nobs, this._domain, this.weightedSigma(),
              this._customMetric, hglmM._output._iterations, hglmM._output._beta, hglmM._output._ubeta,
              tmat, hglmM._output._tau_e_var, mse, this._yMinusfixPredSquare / hglmM._output._nobs, 
              hglmM._output._yminusxtimesz_score, hglmM._output._arjtarj_score);
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
      mm = new ModelMetricsRegressionHGLM(m, f, engineTask._nobs, this._domain, this.weightedSigma(), 
              this._customMetric, hglmM._output._iterations, hglmM._output._beta, hglmM._output._ubeta,
              tmat, hglmM._output._tau_e_var,this._sse/engineTask._nobs, 
              this._yMinusfixPredSquare/engineTask._nobs, hglmM._output._yminusxtimesz_valid, 
              engineTask._ArjTArj);
      hglmM._output._nobs_valid = engineTask._nobs;
    }

    if (m != null)
      m.addModelMetrics(mm);
    return mm;
  }
}
