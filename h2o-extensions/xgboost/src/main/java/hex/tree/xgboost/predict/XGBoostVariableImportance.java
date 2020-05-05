package hex.tree.xgboost.predict;

import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.Map;

public interface XGBoostVariableImportance {

    Map<String, FeatureScore> getFeatureScores() throws XGBoostError;

}
