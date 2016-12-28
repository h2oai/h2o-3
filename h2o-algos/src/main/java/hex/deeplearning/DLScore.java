package hex.deeplearning;

import hex.BigScore;
import hex.Model;
import water.Job;
import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * Pulled out from Model
 */
public class DLScore extends BigScore {
  public SimpleDLM dlm;
  public hex.deeplearning.DlInput inputData() {
    return dlm.get_params().trainData;
  }

  public DLScore(Model model, String[] domain, int ncols, double[] mean, boolean testHasWeights, boolean computeMetrics, boolean makePreds, Job j) {
    super(model, domain, ncols, mean, testHasWeights, computeMetrics, makePreds, j);
    dlm = (SimpleDLM) model;
  }

  @Override
  public void map(Chunk chks[], NewChunk cpreds[]) {
    if (isCancelled() || _j != null && _j.stop_requested()) return;
    float[] actual = null;
    _mb = dlm.makeMetricBuilder(_domain);
    if (_computeMetrics) {
      actual = new float[dlm.isSupervised() ? 1 : chks.length];
    }
    double[] preds = _mb._work;  // Sized for the union of test and train classes
    int len = chks[0]._len;
    for (int row = 0; row < len; row++) {
      final int absIdx = (int)(row + chks[0].start());
      double[] p = dlm.scoreRow((int) absIdx);
      if (_computeMetrics) {
        if (dlm.isSupervised()) {
          actual[0] = inputData().target(absIdx);
        } else {
          for (int i = 0; i < actual.length; ++i) {
            actual[i] = (float)inputData().weight(absIdx, i);
          }
            
        }
        _mb.perRow(preds, actual, 1, 0, dlm);
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
