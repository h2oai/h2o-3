package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.Model;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import ml.dmlc.xgboost4j.java.XGBoostModelInfo;
import ml.dmlc.xgboost4j.java.XGBoostScoreTask;
import water.fvec.Chunk;
import water.fvec.Frame;

public class XGBoostBigScorePredict implements Model.BigScorePredict {

  private final XGBoostModelInfo _modelInfo; 
  private final XGBoostModel.XGBoostParameters _parms;
  private final XGBoostOutput _output;
  private final DataInfo _dataInfo;
  private final BoosterParms _boosterParms;
  private final double _threshold;
  

  public XGBoostBigScorePredict(
      XGBoostModelInfo modelInfo, XGBoostModel.XGBoostParameters parms,
      XGBoostOutput output, DataInfo dataInfo, BoosterParms boosterParms, 
      double threshold
  ) {
    _modelInfo = modelInfo;
    _parms = parms;
    _output = output;
    _dataInfo = dataInfo;
    _boosterParms = boosterParms;
    _threshold = threshold;
  }

  @Override
  public Model.BigScoreChunkPredict initMap(Frame fr, Chunk[] chks) {
    float[][] preds = scoreChunk(fr, chks);
    return new XGBoostBigScoreChunkPredict(_output.nclasses(), preds, _threshold);
  }

  private float[][] scoreChunk(Frame fr, Chunk[] chks) {
    return XGBoostScoreTask.scoreChunk(_modelInfo, _dataInfo, _parms, _boosterParms, _output, fr, chks);
  }
}
