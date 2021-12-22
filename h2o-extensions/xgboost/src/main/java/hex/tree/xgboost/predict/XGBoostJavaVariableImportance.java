package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.RegTreeNode;
import biz.k11i.xgboost.tree.RegTreeNodeStat;
import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.XGBoostUtils;
import hex.tree.xgboost.util.FeatureScore;

import java.util.HashMap;
import java.util.Map;

public class XGBoostJavaVariableImportance implements XGBoostVariableImportance {
    
    private final String[] _featureNames;
    
    public XGBoostJavaVariableImportance(XGBoostModelInfo modelInfo) {
        _featureNames = XGBoostUtils.assembleFeatureNames(modelInfo.dataInfo())._names;
    }

    @Override
    public Map<String, FeatureScore> getFeatureScores(byte[] boosterBytes) {
        Predictor predictor = PredictorFactory.makePredictor(boosterBytes, null, false);
        Map<String, FeatureScore> featureScore = new HashMap<>();
        if (!(predictor.getBooster() instanceof GBTree)) {
            return featureScore;
        }
        GBTree gbm = (GBTree) predictor.getBooster();
        final RegTree[][] trees = gbm.getGroupedTrees();
        for (final RegTree[] treeGroup : trees) {
            for (int j = 0; j < treeGroup.length; j++) {
                RegTree t = treeGroup[j];
                for (int k = 0; k < t.getNodes().length; k++) {
                    RegTreeNode node = t.getNodes()[k];
                    if (node.isLeaf()) continue;
                    RegTreeNodeStat stat = t.getStats()[k];
                    FeatureScore fs = new FeatureScore();
                    fs._gain = stat.getGain();
                    fs._cover = stat.getCover();
                    final String fid = _featureNames[node.getSplitIndex()];
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
