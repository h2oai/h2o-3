package hex.tree.xgboost.exec;

import hex.tree.xgboost.util.FeatureScore;

import java.util.Map;

public interface XGBoostExecutor {
    
    byte[] setup();

    void update(int treeId);
    
    byte[] updateBooster();

    Map<String, FeatureScore> getFeatureScores();

    void cleanup();
}
