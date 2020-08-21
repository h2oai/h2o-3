package hex.tree.xgboost.predict;

import hex.tree.xgboost.util.FeatureScore;

import java.util.Map;

public interface XGBoostVariableImportance {

    Map<String, FeatureScore> getFeatureScores(byte[] boosterBytes);

    default void cleanup() {}

}
