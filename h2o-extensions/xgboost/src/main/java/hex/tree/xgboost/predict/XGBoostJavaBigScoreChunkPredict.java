package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import hex.DataInfo;
import hex.Model;
import hex.genmodel.algos.xgboost.XGBoostMojoModel;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import water.fvec.Chunk;
import water.fvec.Frame;

public class XGBoostJavaBigScoreChunkPredict implements XGBoostPredict, Model.BigScoreChunkPredict {

  private final XGBoostOutput _output;
  private final double _threshold;
  private final Predictor _predictor;
  private final MutableOneHotEncoderFVec _row;
  private final int _offsetIndex;
  private final boolean[] _usedColumns;

  public XGBoostJavaBigScoreChunkPredict(
      DataInfo di,
      XGBoostOutput output,
      XGBoostModel.XGBoostParameters parms,
      double threshold,
      Predictor predictor,
      boolean[] usedColumns,
      Frame data
  ) {
    _output = output;
    _threshold = threshold;
    _predictor = predictor;
    _row = new MutableOneHotEncoderFVec(di, _output._sparse);

    _offsetIndex = data.find(parms._offset_column);
    _usedColumns = usedColumns;
  }

  @Override
  public double[] score0(Chunk[] chks, double offset, int row_in_chunk, double[] tmp, double[] preds) {
    assert _output.nfeatures() == tmp.length;
    for (int i = 0; i < tmp.length; i++) {
      if (_usedColumns == null || _usedColumns[i]) {
        tmp[i] = chks[i].atd(row_in_chunk);
      }
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

  public float[][] predict(Chunk[] cs) {
    final float[][] preds = new float[cs[0]._len][];
    final double[] tmp = new double[_output.nfeatures()];
    for (int row = 0; row < cs[0]._len; row++) {
      for (int col = 0; col < tmp.length; col++) {
        if (_usedColumns == null || _usedColumns[col]) {
          tmp[col] = cs[col].atd(row);
        }
      }
      _row.setInput(tmp);
      if (_offsetIndex >= 0) {
        float offset = (float) cs[_offsetIndex].atd(row);
        preds[row] = _predictor.predict(_row, offset);
      } else {
        preds[row] = _predictor.predict(_row);
      }
    }
    return preds;
  }

  @Override
  public void close() {}
}
