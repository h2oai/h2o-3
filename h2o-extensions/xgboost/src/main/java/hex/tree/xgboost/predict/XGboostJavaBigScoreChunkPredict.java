package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import hex.DataInfo;
import hex.Model;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import water.fvec.Chunk;

public class XGboostJavaBigScoreChunkPredict implements Model.BigScoreChunkPredict {

  private final XGBoostOutput _output;
  private final double _threshold;
  private final Predictor _predictor;
  private final MutableOneHotEncoderFVec _row;

  public XGboostJavaBigScoreChunkPredict(DataInfo di, XGBoostOutput output, double threshold, Predictor predictor) {
    _output = output;
    _threshold = threshold;
    _predictor = predictor;
    _row = new MutableOneHotEncoderFVec(di, _output._sparse);
  }

  @Override
  public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
    assert _output.nfeatures() == tmp.length;
    for (int i = 0; i < tmp.length; i++) {
      tmp[i] = chks[i].atd(row_in_chunk);
    }

    _row.setInput(tmp);

    float[] out;
    if (_output.hasOffset()) {
      out = _predictor.predict(_row, (float) offset);
    } else if (offset != 0) {
      throw new IllegalArgumentException("Model was not trained with offset_column, but offset != 0");
    } else {
      out = _predictor.predict(_row);

    }

    return XGBoostMojoModel.toPreds(tmp, out, preds, _output.nclasses(), _output._priorClassDist, _threshold);
  }

  @Override
  public void close() {}
}
