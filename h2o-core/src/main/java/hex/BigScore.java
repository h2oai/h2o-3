package hex;

import water.Job;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * Pulled out from Model
 */
public class BigScore extends MRTask<BigScore> {
  final protected Model model;
  final protected String[] _domain; // Prediction domain; union of test and train classes
  final protected int _npredcols;  // Number of columns in prediction; nclasses+1 - can be less than the prediction domain
  public ModelMetrics.MetricBuilder _mb;
  final double[] _mean;  // Column means of test frame
  final public boolean _computeMetrics;  // Column means of test frame
  final public boolean _hasWeights;
  final public boolean _makePreds;
  final public Job _j;

  public BigScore(Model model, String[] domain, int ncols, double[] mean, boolean testHasWeights, boolean computeMetrics, boolean makePreds, Job j) {
    this.model = model;
    _j = j;
    _domain = domain;
    _npredcols = ncols;
    _mean = mean;
    _computeMetrics = computeMetrics;
    _makePreds = makePreds;
    if (model._output._hasWeights && _computeMetrics && !testHasWeights)
      throw new IllegalArgumentException("Missing weights when computing validation metrics.");
    _hasWeights = testHasWeights;
  }

  @Override
  public void map(Chunk chks[], NewChunk cpreds[]) {
    if (isCancelled() || _j != null && _j.stop_requested()) return;
    Chunk weightsChunk = _hasWeights && _computeMetrics ? chks[model._output.weightsIdx()] : null;
    Chunk offsetChunk = model._output.hasOffset() ? chks[model._output.offsetIdx()] : null;
    Chunk responseChunk = null;
    double[] tmp = new double[model._output.nfeatures()];
    float[] actual = null;
    _mb = model.makeMetricBuilder(_domain);
    if (_computeMetrics) {
      if (model.isSupervised()) {
        actual = new float[1];
        responseChunk = chks[model._output.responseIdx()];
      } else
        actual = new float[chks.length];
    }
    double[] preds = _mb._work;  // Sized for the union of test and train classes
    int len = chks[0]._len;
    for (int row = 0; row < len; row++) {
      double weight = weightsChunk != null ? weightsChunk.atd(row) : 1;
      if (weight == 0) {
        if (_makePreds) {
          for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
            cpreds[c].addNum(0);
        }
        continue;
      }
      double offset = offsetChunk != null ? offsetChunk.atd(row) : 0;
      double[] p = model.score0(chks, weight, offset, row, tmp, preds);
      if (_computeMetrics) {
        if (model.isSupervised()) {
          actual[0] = (float) responseChunk.atd(row);
        } else {
          for (int i = 0; i < actual.length; ++i)
            actual[i] = (float) chks[i].atd(row);
        }
        _mb.perRow(preds, actual, weight, offset, model);
      }
      if (_makePreds) {
        for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
          cpreds[c].addNum(p[c]);
      }
    }
    if (_j != null) _j.update(1);
  }

  @Override
  public void reduce(BigScore bs) {
    if (_mb != null) _mb.reduce(bs._mb);
  }

  @Override
  protected void postGlobal() {
    if (_mb != null) _mb.postGlobal();
  }
}
