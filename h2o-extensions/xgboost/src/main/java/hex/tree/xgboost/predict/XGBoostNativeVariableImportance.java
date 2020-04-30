package hex.tree.xgboost.predict;

import hex.tree.xgboost.XGBoostModelInfo;
import hex.tree.xgboost.util.BoosterHelper;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.Booster;
import ml.dmlc.xgboost4j.java.XGBoostError;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class XGBoostNativeVariableImportance implements XGBoostVariableImportance {
    
    private final byte[] boosterBytes;
    private final File featureMapFile;

    public XGBoostNativeVariableImportance(XGBoostModelInfo modelInfo, File featureMapFile) {
        this.boosterBytes = modelInfo._boosterBytes;
        this.featureMapFile = featureMapFile;
    }

    public Map<String, FeatureScore> getFeatureScores() throws XGBoostError {
        Booster booster = null;
        try {
            booster = BoosterHelper.loadModel(boosterBytes);
            return BoosterHelper.doWithLocalRabit(new BoosterHelper.BoosterOp<Map<String, FeatureScore>>() {
                @Override
                public Map<String, FeatureScore> apply(Booster booster) throws XGBoostError {
                    String fmPath = featureMapFile.getAbsolutePath();
                    final String[] modelDump = booster.getModelDump(fmPath, true);
                    return parseFeatureScores(modelDump);
                }
            }, booster);
        } finally {
            if (booster != null)
                BoosterHelper.dispose(booster);
        }
    }

    public static Map<String, FeatureScore> parseFeatureScores(String[] modelDump) {
        Map<String, FeatureScore> featureScore = new HashMap<>();
        for (String tree : modelDump) {
            for (String node : tree.split("\n")) {
                String[] array = node.split("\\[", 2);
                if (array.length < 2)
                    continue;
                String[] content = array[1].split("\\]", 2);
                if (content.length < 2)
                    continue;
                String fid = content[0].split("<")[0];

                FeatureScore fs = new FeatureScore();
                String[] keyValues = content[1].split(",");
                for (String keyValue : keyValues) {
                    if (keyValue.startsWith(FeatureScore.GAIN_KEY + "=")) {
                        fs._gain = Float.parseFloat(keyValue.substring(FeatureScore.GAIN_KEY.length() + 1));
                    } else if (keyValue.startsWith(FeatureScore.COVER_KEY + "=")) {
                        fs._cover = Float.parseFloat(keyValue.substring(FeatureScore.COVER_KEY.length() + 1));
                    }
                }
                if (featureScore.containsKey(fid)) {
                    featureScore.get(fid).add(fs);
                } else {
                    featureScore.put(fid, fs);
                }
            }
        }
        return featureScore;
    }
}
