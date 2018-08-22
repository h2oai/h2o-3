package ml.dmlc.xgboost4j.java;

import biz.k11i.xgboost.Predictor;
import hex.DataInfo;
import hex.genmodel.algos.xgboost.XGBoostJavaMojoModel;
import hex.tree.xgboost.XGBoost;
import water.Iced;
import water.Key;

import java.util.Arrays;


/**
 * This class contains the state of the Deep Learning model
 * This will be shared: one per node
 */
final public class XGBoostModelInfo extends Iced {
  public final Key<DataInfo> _dataInfoKey;
  private String _featureMap;
  public byte[] _boosterBytes; // internal state of native backend

  private transient Predictor _predictor; // cached predictor

  public XGBoostModelInfo(Key<DataInfo> dataInfoKey) {
    _dataInfoKey = dataInfoKey;
  }


  public String getFeatureMap() {
    return _featureMap;
  }

  public void setFeatureMap(String featureMap) {
    _featureMap = featureMap;
  }

  public void updateBooster(Booster booster) {
    _boosterBytes = XGBoost.getRawArray(booster);
    _predictor = null;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(_boosterBytes);
  }

  public final Predictor getPredictor() {
    if (_predictor == null) {
      _predictor = XGBoostJavaMojoModel.makePredictor(_boosterBytes);
    }
    return _predictor;
  }

}
