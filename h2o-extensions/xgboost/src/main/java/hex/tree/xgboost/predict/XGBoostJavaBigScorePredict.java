package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import hex.DataInfo;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import hex.tree.xgboost.XGBoostModelInfo;
import water.fvec.Chunk;
import water.fvec.Frame;

public class XGBoostJavaBigScorePredict implements XGBoostBigScorePredict {

  private final DataInfo _di;
  private final XGBoostOutput _output;
  private final XGBoostModel.XGBoostParameters _parms;
  private final double _threshold;
  private final Predictor _predictor;

  public XGBoostJavaBigScorePredict(
      XGBoostModelInfo model_info,
      XGBoostOutput output,
      DataInfo di,
      XGBoostModel.XGBoostParameters parms, double threshold
  ) {
    _di = di;
    _output = output;
    _parms = parms;
    _threshold = threshold;
    _predictor = PredictorFactory.makePredictor(model_info._boosterBytes);
  }

  @Override
  public XGBoostPredict initMap(Frame fr, Chunk[] chks) {
    return new XGBoostJavaBigScoreChunkPredict(_di, _output, _parms, _threshold, _predictor, fr);
  }

}
