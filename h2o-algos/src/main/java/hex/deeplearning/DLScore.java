package hex.deeplearning;

import hex.BigScore;
import hex.Model;
import water.H2O;
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
    final long chunkOffset = chks[0].start();
    final int len = chks[0]._len;
    final int numCols = chks.length;

    map1(cpreds, chunkOffset, len, numCols);
    if (_j != null) _j.update(1);
  }

  static int patience = 1;
  
  void map1(NewChunk[] cpreds, long chunkOffset, int len, int numCols) {
    if (patience --> 0) Thread.dumpStack();
    float[] actual = null;
    _mb = dlm.makeMetricBuilder(_domain);
    if (_computeMetrics) {
      actual = new float[dlm.isSupervised() ? 1 : numCols];
    }
    for (int row = 0; row < len; row++) {
      final int absIdx = (int)(row + chunkOffset);
      double[] p = dlm.scoreRow((int) absIdx);
      if (_computeMetrics) {
        if (dlm.isSupervised()) {
          actual[0] = inputData().target(absIdx);
        } else {
          for (int i = 0; i < actual.length; ++i) {
            actual[i] = (float)inputData().weight(absIdx, i);
          }
        }
//        System.out.println("\n--------------\nmb=" + _mb.getClass() + ", act=" + actual);
// mb=class hex.ModelMetricsBinomial$MetricBuilderBinomial, act=[F@a62129c
        // TODO(vlad): figure out if there's any influence at all
//        _mb.perRow(_mb._work, actual, dlm);
      }
      if (_makePreds) {
//        System.out.println("****** MAKING PREDS ******* " + _npredcols);
        for (int c = 0; c < _npredcols; c++)  // Output predictions; sized for train only (excludes extra test classes)
          cpreds[c].addNum(p[c]);
      }
    }
  }

  public void reduce(DLScore bs) {
    throw H2O.unimpl("reduce what?");
//    if (_mb != null) _mb.reduce(bs._mb);
  }

  @Override
  protected void postGlobal() {}
}
