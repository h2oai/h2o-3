package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostOutput;
import water.fvec.Chunk;
import water.fvec.Frame;

public class XGBoostNativeBigScorePredict implements XGBoostBigScorePredict {

  private final XGBoostModelInfo _modelInfo; 
  private final XGBoostModel.XGBoostParameters _parms;
  private final XGBoostOutput _output;
  private final DataInfo _dataInfo;
  private final double _threshold;
  

  public XGBoostNativeBigScorePredict(
      XGBoostModelInfo modelInfo, XGBoostModel.XGBoostParameters parms,
      XGBoostOutput output, DataInfo dataInfo, double threshold
  ) {
    _modelInfo = modelInfo;
    _parms = parms;
    _output = output;
    _dataInfo = dataInfo;
    _threshold = threshold;
  }

  @Override
  public XGBoostPredict initMap(Frame fr, Chunk[] chks) {
    return new XGBoostNativeBigScoreChunkPredict(_modelInfo, _parms, _dataInfo, _threshold, _output, fr, chks);
  }

}
