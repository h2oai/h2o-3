package hex.tree.xgboost.predict;

import hex.tree.xgboost.util.BoosterHelper;
import hex.tree.xgboost.util.FeatureScore;
import ai.h2o.xgboost4j.java.Booster;
import ai.h2o.xgboost4j.java.XGBoostError;
import org.apache.log4j.Logger;
import water.Key;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class XGBoostNativeVariableImportance implements XGBoostVariableImportance {

    private static final Logger LOG = Logger.getLogger(XGBoostNativeVariableImportance.class);

    private final File featureMapFile;

    public XGBoostNativeVariableImportance(Key modelKey, String featureMap) {
        featureMapFile = createFeatureMapFile(modelKey, featureMap);
    }

    private File createFeatureMapFile(Key modelKey, String featureMap) {
        try {
            File fmFile = Files.createTempFile("h2o_xgb_" + modelKey.toString(), ".txt").toFile();
            fmFile.deleteOnExit();
            try (OutputStream os = new FileOutputStream(fmFile)) {
                os.write(featureMap.getBytes());
            }
            return fmFile;
        } catch (IOException e) {
            throw new RuntimeException("Cannot generate feature map file", e);
        }
    }

    @Override
    public void cleanup() {
        if (featureMapFile != null) {
            if (!featureMapFile.delete()) {
                LOG.warn("Unable to delete file " + featureMapFile + ". Please do a manual clean-up.");
            }
        }
    }

    public Map<String, FeatureScore> getFeatureScores(byte[] boosterBytes) {
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
        } catch (XGBoostError e) {
            throw new RuntimeException("Failed getting feature scores.", e);
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
