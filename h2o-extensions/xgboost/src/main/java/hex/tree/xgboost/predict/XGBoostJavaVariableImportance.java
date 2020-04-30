package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeImpl;
import biz.k11i.xgboost.tree.RegTreeNode;
import biz.k11i.xgboost.tree.RegTreeNodeStat;
import hex.DataInfo;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostUtils;
import hex.tree.xgboost.util.FeatureScore;

import java.util.HashMap;
import java.util.Map;

public class XGBoostJavaVariableImportance implements XGBoostVariableImportance {
    
    private final Predictor predictor;
    private final DataInfo dataInfo;
    
    public XGBoostJavaVariableImportance(XGBoostModelInfo modelInfo) {
        predictor = PredictorFactory.makePredictor(modelInfo._boosterBytes, false);
        dataInfo = modelInfo.dataInfo();
    }

    @Override
    public Map<String, FeatureScore> getFeatureScores() {
        Map<String, FeatureScore> featureScore = new HashMap<>();
        if (!(predictor.getBooster() instanceof GBTree)) {
            return featureScore;
        }
        GBTree gbm = (GBTree) predictor.getBooster();
        final XGBoostUtils.FeatureProperties featureProperties = XGBoostUtils.assembleFeatureNames(dataInfo);
        final RegTree[][] trees = gbm.getGroupedTrees();
        for (int i = 0; i < trees.length; i++) {
            for (int j = 0; j < trees[i].length; j++) {
                RegTreeImpl t = (RegTreeImpl) trees[i][j];
                for (int k = 0; k < t.getNodes().length; k++) {
                    RegTreeNode node = t.getNodes()[k];
                    if (node.isLeaf()) continue;
                    RegTreeNodeStat stat = t.getStats()[k];
                    FeatureScore fs = new FeatureScore();
                    fs._gain = stat.getGain();
                    fs._cover = stat.getCover();
                    String fid = featureProperties._names[node.getSplitIndex()];
                    if (featureScore.containsKey(fid)) {
                        featureScore.get(fid).add(fs);
                    } else {
                        featureScore.put(fid, fs);
                    }
                }
            }
        }
        return featureScore;
    }

}
