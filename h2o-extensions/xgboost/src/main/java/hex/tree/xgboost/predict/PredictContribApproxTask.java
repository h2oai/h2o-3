package hex.tree.xgboost.predict;

import hex.DataInfo;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostOutput;
import ml.dmlc.xgboost4j.java.XGBoostModelInfo;
import ml.dmlc.xgboost4j.java.XGBoostScoreTask;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.NewChunk;

public class PredictContribApproxTask extends MRTask<PredictContribApproxTask> {

  private final XGBoostModel.XGBoostParameters _parms;
  private final XGBoostModelInfo _modelInfo;
  private final XGBoostOutput _output;
  private final BoosterParms _boosterParms;

  public PredictContribApproxTask(
      XGBoostModel.XGBoostParameters parms,
      XGBoostModelInfo modelInfo,
      XGBoostOutput output, DataInfo di
  ) {
    _parms = parms;
    _modelInfo = modelInfo;
    _output = output;
    _boosterParms = XGBoostModel.createParams(_parms, _output.nclasses(), di.coefNames());
  }

  @Override
  public void map(Chunk chks[], NewChunk[] nc) {
    DataInfo dataInfo = _modelInfo.scoringInfo(false);
    float[][] contrib = XGBoostScoreTask.scoreChunkContribApprox(_modelInfo, dataInfo, _parms, _boosterParms, _output, _fr, chks);
    for (float[] rowContrib : contrib) {
      for (int j = 0; j < rowContrib.length; j++) {
        nc[j].addNum(rowContrib[j]);
      }
    }
  }
}
