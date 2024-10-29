package hex.hglm;

import hex.DataInfo;
import water.Job;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Random;

import static hex.hglm.HGLMTask.ComputationEngineTask.fillInFixedRowValues;
import static hex.hglm.HGLMTask.ComputationEngineTask.fillInRandomRowValues;
import static water.util.ArrayUtils.innerProduct;

public class HGLMScore extends MRTask<HGLMScore> {
  // the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
  // I will be referring to the doc and different parts of it to explain my implementation.
  DataInfo _dinfo;
  double[] _beta;     // non-standardized coefficients
  double[][] _ubeta;  // non-standardized coefficients
  final Job _job;
  boolean _computeMetrics;
  boolean _makePredictions;
  final HGLMModel _model;
  MetricBuilderHGLM _mb;
  String[] _predDomains;
  int _nclass;
  HGLMModel.HGLMParameters _parms;
  int _level2UnitIndex;
  int[] _fixedCatIndices;
  int _numLevel2Units;
  int _predStartIndexFixed;
  int[] _randomCatIndices;
  int[] _randomNumIndices;
  int[] _randomCatArrayStartIndices;
  int _predStartIndexRandom;
  final boolean _randomSlopeToo;
  final boolean _randomIntercept; // true if present
  public double[][] _yMinusXTimesZ; // use non-normalized coefficients
  double[][] _tmat;
  Random randomObj;
  final double _noiseStd;
  
  public HGLMScore(final Job j, final HGLMModel model, DataInfo dinfo, final String[] respDomain, 
                   final boolean computeMetrics, final boolean makePredictions) {
    _job = j;
    _model = model;
    _dinfo = dinfo;
    _computeMetrics = computeMetrics; // can be true only if the response column is available and calcualte loglikelihood
    _makePredictions = makePredictions;
    _beta = model._output._beta;    // non-standardized/non-normalized coefficients
    _ubeta = model._output._ubeta;  // non-standardized/non-normalized coefficients
    _predDomains = respDomain;
    _nclass = model._output.nclasses();
    _parms = model._parms;
    _level2UnitIndex = model._output._level2UnitIndex;
    _fixedCatIndices = model._output._fixedCatIndices;
    _numLevel2Units = model._output._numLevel2Units;
    _predStartIndexFixed = model._output._predStartIndexFixed;
    _randomCatIndices = model._output._randomCatIndices;
    _randomNumIndices = model._output._randomNumIndices;
    _randomCatArrayStartIndices = model._output._randomCatArrayStartIndices;
    _predStartIndexRandom = model._output._predStartIndexRandom;
    _randomSlopeToo = model._output._randomSlopeToo;
    _randomIntercept = _parms._random_intercept;
    _tmat = model._output._tmat;  // generated from non-standardized random coefficients
    randomObj = new Random(_parms._seed);
    _noiseStd = Math.sqrt(_parms._tau_e_var_init);  // not affected by standardization/normalization
  }
  
  @Override
  public void map(Chunk[] chks, NewChunk[] nc) {
    if (isCancelled() || (_job != null && _job.stop_requested()))  return;
    float[] response = null; // store response column value if exists
    int numPredValues = _nclass <= 1 ? 1 : _nclass + 1;
    double[] predictVals = MemoryManager.malloc8d(numPredValues);
    double[] xji = MemoryManager.malloc8d(_model._output._beta.length);
    double[] zji = MemoryManager.malloc8d(_model._output._ubeta[0].length);
    if (_computeMetrics) {
      _mb = (MetricBuilderHGLM) _model.makeMetricBuilder(_predDomains);
      response = new float[1];
      _yMinusXTimesZ = new double[_numLevel2Units][zji.length];
    }
    DataInfo.Row r = _dinfo.newDenseRow();
    
    if (_computeMetrics && (r.response == null || r.response.length == 0))
      throw new IllegalArgumentException("computeMetrics can only be set to true if the response column exists in" +
              " dataset passed to prediction function.");
    int chkLen = chks[0].len();
    int level2Index;
    for (int rid = 0; rid < chkLen; rid++) {
      _dinfo.extractDenseRow(chks, rid, r);
      level2Index = _parms._use_all_factor_levels ? r.binIds[_level2UnitIndex] - _dinfo._catOffsets[_level2UnitIndex] :
              (int) chks[_level2UnitIndex].at8(rid);
      processRow(r, predictVals, nc, numPredValues, xji, zji, level2Index);
      if (_computeMetrics && !r.response_bad) { // calculate metrics
        response[0] = (float) r.response[0];
        _mb.perRow(predictVals, response, r.weight, r.offset, xji, zji, _yMinusXTimesZ, level2Index, _model);
      }
    }
  }
  
  @Override
  public void reduce(HGLMScore other) {
    if (_mb != null)
      _mb.reduce(other._mb);
    if (_computeMetrics)
      ArrayUtils.add(_yMinusXTimesZ, other._yMinusXTimesZ);
  }
  
  private void processRow(DataInfo.Row r, double[] ps, NewChunk[] preds, int numPredCols, double[] xji, double[] zji,
                          int level2Index) {
    if (r.predictors_bad) {
      Arrays.fill(ps, Double.NaN);
      return;
    } else if (r.weight == 0) {
      Arrays.fill(ps, 0.0);
      return;
    }
    ps = scoreRow(r, ps, xji, zji, level2Index); // weight is not zero and response is valid
    if (_makePredictions)
      for (int predCol = 0; predCol < numPredCols; predCol++) { // write prediction to NewChunk
        preds[predCol].addNum(ps[predCol]);
      }
  }

  /**
   * only processing gaussian for now.
   */
  public double[] scoreRow(DataInfo.Row r, double[] preds, double[] xji, double[] zji, int level2Index) {
    fillInFixedRowValues(r, xji, _parms, _fixedCatIndices, _level2UnitIndex,
            _numLevel2Units, _predStartIndexFixed, _dinfo);
    fillInRandomRowValues(r, zji, _parms, _randomCatIndices, _randomNumIndices, _randomCatArrayStartIndices,
            _predStartIndexRandom, _dinfo, _randomSlopeToo, _randomIntercept);
    preds[0] = innerProduct(xji, _beta) + innerProduct(zji, _ubeta[level2Index]) + r.offset;
    preds[0] = _parms._gen_syn_data ? preds[0]+randomObj.nextGaussian()*_noiseStd : preds[0];
    return preds;
  }
}
