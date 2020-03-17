package hex.tree.xgboost.exec;

import ml.dmlc.xgboost4j.java.XGBoostModelInfo;
import ml.dmlc.xgboost4j.java.XGBoostUpdateTask;

import java.io.File;

public final class BoosterProvider {

  public final XGBoostModelInfo _modelInfo;
  public final File _featureMapFile;
  XGBoostUpdateTask _updateTask;

  BoosterProvider(XGBoostModelInfo modelInfo, File featureMapFile, XGBoostUpdateTask updateTask) {
    _modelInfo = modelInfo;
    _featureMapFile = featureMapFile;
    _updateTask = updateTask;
    _modelInfo.setBoosterBytes(_updateTask.getBoosterBytes());
  }

  public void reset(XGBoostUpdateTask updateTask) {
    _updateTask = updateTask;
  }

  public void updateBooster() {
    if (_updateTask == null) {
      throw new IllegalStateException("Booster can be retrieved only once!");
    }
    final byte[] boosterBytes = _updateTask.getBoosterBytes();
    _modelInfo.setBoosterBytes(boosterBytes);
  }
}
