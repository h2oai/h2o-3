package hex.deeplearning;

import hex.BigScore;
import hex.Model;
import hex.ModelMetrics;
import water.Job;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * Pulled out from Model
 */
public class DLScore extends BigScore {
  public hex.deeplearning.DlInput inputData() {
    return ((DeepLearningModel)model).get_params().trainData;
  }

  public DLScore(Model model, String[] domain, int ncols, double[] mean, boolean testHasWeights, boolean computeMetrics, boolean makePreds, Job j) {
    super(model, domain, ncols, mean, testHasWeights, computeMetrics, makePreds, j);
  }

  @Override
  public void map(Chunk chks[], NewChunk cpreds[]) {
    if (isCancelled() || _j != null && _j.stop_requested()) return;
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
      double weight = 1;
      double offset = 0;
      double[] p = model.score0(chks, weight, offset, row, tmp, preds);
      final long absIdx = row + chks[0].start();
      if (_computeMetrics) {
        if (model.isSupervised()) {
          actual[0] = inputData().target((int) absIdx);
        } else {
          for (int i = 0; i < actual.length; ++i) {
            actual[i] = (float) chks[i].atd(row);
            final double altValue = inputData().weight((int)absIdx, i);
            assert(actual[i] == (float)altValue) : "Failed at " + absIdx;
          }
            
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

  public void reduce(DLScore bs) {
    if (_mb != null) _mb.reduce(bs._mb);
  }

  @Override
  protected void postGlobal() {
    if (_mb != null) _mb.postGlobal();
  }
}
