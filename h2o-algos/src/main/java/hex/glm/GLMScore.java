package hex.glm;

import hex.DataInfo;
import hex.ModelMetrics;
import water.Job;
import water.MRTask;
import water.MemoryManager;
import water.fvec.Chunk;
import water.fvec.NewChunk;
import water.util.ArrayUtils;
import water.util.FrameUtils;

import java.util.Arrays;

/**
 * Created by tomas on 3/15/16.
 */
public class GLMScore extends MRTask<GLMScore> {
  final GLMModel _m;
  final Job _j;
  ModelMetrics.MetricBuilder _mb;
  final DataInfo _dinfo;
  final boolean _sparse;
  final String[] _domain;
  final boolean _computeMetrics;
  final boolean _generatePredictions;
  transient double [][] _vcov;
  transient double [] _tmp;
  transient double [] _eta;
  final int _nclasses;
  private final double []_beta;
  private final double [][] _beta_multinomial;
  private final double _defaultThreshold;



  public GLMScore(Job j, GLMModel m, DataInfo dinfo, String[] domain, boolean computeMetrics, boolean generatePredictions) {
    _j = j;
    _m = m;
    _computeMetrics = computeMetrics;
    _sparse = FrameUtils.sparseRatio(dinfo._adaptedFrame) < .5;
    _domain = domain;
    _generatePredictions = generatePredictions;
    _m._parms = m._parms;
    _nclasses = m._output.nclasses();

    if(_m._parms._family == GLMModel.GLMParameters.Family.multinomial ||
            _m._parms._family == GLMModel.GLMParameters.Family.ordinal){
      _beta = null;
      _beta_multinomial = m._output._global_beta_multinomial;
    } else {
      double [] beta = m.beta();
      int [] ids = new int[beta.length-1];
      int k = 0;
      for(int i = 0; i < beta.length-1; ++i){ // pick out beta that is not zero in ids
        if(beta[i] != 0) ids[k++] = i;
      }
      if(k < beta.length-1) {
        ids = Arrays.copyOf(ids,k);
        dinfo = dinfo.filterExpandedColumns(ids);
        double [] beta2 = MemoryManager.malloc8d(ids.length+1);
        int l = 0;
        for(int x:ids)
          beta2[l++] = beta[x];
        beta2[l] = beta[beta.length-1];
        beta = beta2;
      }
      _beta_multinomial = null;
      _beta = beta;
    }
    _dinfo = dinfo;
    _dinfo._valid = true; // marking dinfo as validation data set disables an assert on unseen levels (which should not happen in train)
    _defaultThreshold = m.defaultThreshold();
  }

  public double [] scoreRow(DataInfo.Row r, double o, double [] preds) {
    int lastClass = _nclasses-1;
    if(_m._parms._family == GLMModel.GLMParameters.Family.ordinal) {  // todo: change this to take various link func
      final double[][] bm = _beta_multinomial;
      Arrays.fill(preds,0); // initialize to small number
      preds[0] = lastClass;  // initialize to last class by default here
      double previousCDF = 0.0;
      for (int cInd = 0; cInd < lastClass; cInd++) { // classify row and calculate PDF of each class
        double eta = r.innerProduct(bm[cInd]) + o;
        double currCDF = 1.0 / (1 + Math.exp(-eta));
        preds[cInd + 1] = currCDF - previousCDF;
        previousCDF = currCDF;

        if (eta > 0) { // found the correct class
          preds[0] = cInd;
          break;
        }
      }
      for (int cInd = (int) preds[0] + 1; cInd < lastClass; cInd++) {  // continue PDF calculation
        double currCDF = 1.0 / (1 + Math.exp(-r.innerProduct(bm[cInd]) + o));
        preds[cInd + 1] = currCDF - previousCDF;
        previousCDF = currCDF;

      }
      preds[_nclasses] = 1-previousCDF;
    } else if (_m._parms._family == GLMModel.GLMParameters.Family.multinomial) {
      double[] eta = _eta;
      final double[][] bm = _beta_multinomial;
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
      double mu = _m._parms.linkInv(r.innerProduct(_beta) + o);
      if (_m._parms._family == GLMModel.GLMParameters.Family.binomial 
              || _m._parms._family == GLMModel.GLMParameters.Family.quasibinomial 
              || _m._parms._family == GLMModel.GLMParameters.Family.fractionalbinomial) { // threshold for prediction
        preds[0] = mu >= _defaultThreshold?1:0;
        preds[1] = 1.0 - mu; // class 0
        preds[2] = mu; // class 1
      } else
        preds[0] = mu;
    }
    return preds;
  }

  private void processRow(DataInfo.Row r, float [] res, double [] ps, NewChunk [] preds, int ncols) {
    if(_dinfo._responses != 0)res[0] = (float) r.response[0];
    if (r.predictors_bad) {
      Arrays.fill(ps,Double.NaN);
    } else if(r.weight == 0) {
      Arrays.fill(ps,0);
    } else {
      scoreRow(r, r.offset, ps);
      if (_computeMetrics && !r.response_bad)
        _mb.perRow(ps, res, r.weight, r.offset, _m);
    }
    if (_generatePredictions) {
      for (int c = 0; c < ncols; c++)  // Output predictions; sized for train only (excludes extra test classes)
        preds[c].addNum(ps[c]);
      if(_vcov != null) { // compute standard error on prediction
        preds[ncols].addNum(Math.sqrt(r.innerProduct(r.mtrxMul(_vcov, _tmp))));
      }
    }
  }
  public void map(Chunk[] chks, NewChunk[] preds) {
    if (isCancelled() || _j != null && _j.stop_requested()) return;
    if(_m._parms._family == GLMModel.GLMParameters.Family.multinomial ||
            _m._parms._family == GLMModel.GLMParameters.Family.ordinal)
      _eta = MemoryManager.malloc8d(_nclasses);
    double[] ps;
    _vcov = _m._output._vcov;
    if(_generatePredictions){
      if(_vcov != null){
        _tmp = MemoryManager.malloc8d(_vcov.length);
      }
    }
    if (_computeMetrics) {
      _mb = _m.makeMetricBuilder(_domain);
      ps = _mb._work;  // Sized for the union of test and train classes
    } else
      ps = new double[_m._output._nclasses+1];
    float[] res = new float[1];
    final int nc = _m._output.nclasses();
    final int ncols = nc == 1 ? 1 : nc + 1; // Regression has 1 predict col; classification also has class distribution
    // compute
    if (_sparse) {
      for (DataInfo.Row r : _dinfo.extractSparseRows(chks))
        processRow(r,res,ps,preds,ncols);
    } else {
      DataInfo.Row r = _dinfo.newDenseRow();
      for (int rid = 0; rid < chks[0]._len; ++rid) {
        _dinfo.extractDenseRow(chks, rid, r);
        processRow(r,res,ps,preds,ncols);
      }
    }
    if (_j != null) _j.update(1);
  }

  @Override
  public void reduce(GLMScore bs) {
    if (_mb != null) _mb.reduce(bs._mb);
  }

  @Override
  protected void postGlobal() {
    if (_mb != null) _mb.postGlobal();
  }
}


