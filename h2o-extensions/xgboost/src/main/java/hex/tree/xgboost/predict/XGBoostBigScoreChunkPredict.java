package hex.tree.xgboost.predict;

import hex.Model;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import water.fvec.Chunk;

public class XGBoostBigScoreChunkPredict implements Model.BigScoreChunkPredict {

  private final int _nclasses;
  private final float[][] _preds;
  private final double _threshold;

  public XGBoostBigScoreChunkPredict(int nclasses, float[][] preds, double threshold) {
    _nclasses = nclasses;
    _preds = preds;
    _threshold = threshold;
  }

  @Override
  public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
    for (int i = 0; i < tmp.length; i++) {
      tmp[i] = chks[i].atd(row_in_chunk);
    }
    return XGBoostMojoModel.toPreds(tmp, _preds[row_in_chunk], preds, _nclasses, null, _threshold);
  }

  @Override
  public void close() {}
}
