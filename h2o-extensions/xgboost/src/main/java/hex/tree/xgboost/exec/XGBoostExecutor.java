package hex.tree.xgboost.exec;

import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.util.Map;

public interface XGBoostExecutor {
    
    XGBoostModel getModel();

    void setup();
    
    void update(int treeId);
    
    void updateBooster();

    Map<String, FeatureScore> getFeatureScores() throws XGBoostError;

    void cleanup();
}
