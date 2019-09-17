package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import hex.DataInfo;
import hex.Model;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import ml.dmlc.xgboost4j.java.PredictorFactory;
import water.fvec.Chunk;
import water.fvec.Frame;

public class XGBoostJavaBigScorePredict implements Model.BigScorePredict {

  private final DataInfo _di;
  private final XGBoostOutput _output;
  private final double _threshold;
  private final Predictor _predictor;

  public XGBoostJavaBigScorePredict(DataInfo di, XGBoostOutput output, double threshold, byte[] boosterBytes) {
    _di = di;
    _output = output;
    _threshold = threshold;
    _predictor = PredictorFactory.makePredictor(boosterBytes);
  }

  @Override
  public Model.BigScoreChunkPredict initMap(Frame fr, Chunk[] chks) {
    return new XGboostJavaBigScoreChunkPredict(_di, _output, _threshold, _predictor);
  }

}
