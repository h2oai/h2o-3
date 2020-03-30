package hex.tree.xgboost.exec;

import hex.DataInfo;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import hex.tree.xgboost.rabit.RabitTrackerH2O;
import ml.dmlc.xgboost4j.java.FileXGBoostMatrixFactory;
import hex.tree.xgboost.util.BoosterHelper;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.*;
import water.H2O;
import water.Key;
import water.fvec.Frame;
import water.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class LocalXGBoostExecutor implements XGBoostExecutor {

    public final Key modelKey;
    private final RabitTrackerH2O rt;
    private final XGBoostSetupTask setupTask;
    
    private File featureMapFile;
    private XGBoostUpdateTask updateTask;
    
    private byte[] latestBooster;
    
    /**
     * Used when executing from a remote model
     */
    public LocalXGBoostExecutor(Key key, XGBoostExecReq.Init init) {
        modelKey = key;
        rt = new RabitTrackerH2O(init.num_nodes);
        BoosterParms boosterParams = BoosterParms.fromMap(init.parms);
        boolean[] nodes = new boolean[H2O.CLOUD.size()];
        for (int i = 0; i < init.num_nodes; i++) nodes[i] = true;
        XGBoostMatrixFactory f = new FileXGBoostMatrixFactory(init.matrix_dir_path);
        setupTask = new XGBoostSetupTask(
            modelKey, null, boosterParams, init.checkpoint_bytes, getRabitEnv(), nodes, f
        );
        featureMapFile = createFeatureMapFile(init.featureMap);
    }

    /**
     * Used when executing from a local model
     */
    public LocalXGBoostExecutor(XGBoostModel model, Frame train, XGBoostModel.XGBoostParameters parms) {
        modelKey = model._key;
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        rt = new RabitTrackerH2O(trainFrameNodes.getNumNodes());
        byte[] checkpointBytes = null;
        if (parms.hasCheckpoint()) {
            checkpointBytes = model.model_info()._boosterBytes;
        }
        DataInfo dataInfo = model.model_info().dataInfo();
        BoosterParms boosterParms = XGBoostModel.createParams(parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = boosterParms.toTwoDimTable();
        XGBoostMatrixFactory f = new FrameXGBoostMatrixFactory(model, parms, trainFrameNodes);
        setupTask = new XGBoostSetupTask(
            modelKey, parms._save_matrix_directory, boosterParms, checkpointBytes, getRabitEnv(), trainFrameNodes._nodes, f
        );
        String featureMap = XGBoostUtils.createFeatureMap(model, train);
        featureMapFile = createFeatureMapFile(featureMap);
    }
    
    @Override
    public byte[] setup() {
        startRabitTracker();
        setupTask.run();
        updateTask = new XGBoostUpdateTask(setupTask, 0).run();
        return updateTask.getBoosterBytes();
    }

    // Don't start the tracker for 1 node clouds -> the GPU plugin fails in such a case
    private void startRabitTracker() {
        if (H2O.CLOUD.size() > 1) {
            rt.start(0);
        }
    }

    private void stopRabitTracker() {
        if(H2O.CLOUD.size() > 1) {
            rt.waitFor(0);
            rt.stop();
        }
    }

    // XGBoost seems to manipulate its frames in case of a 1 node distributed version in a way the GPU plugin can't handle
    // Therefore don't use RabitTracker envs for 1 node
    private Map<String, String> getRabitEnv() {
        if(H2O.CLOUD.size() > 1) {
            return rt.getWorkerEnvs();
        } else {
            return new HashMap<>();
        }
    }

    private File createFeatureMapFile(String featureMap) {
        try {
            File fmFile = Files.createTempFile("h2o_xgb_" + modelKey.toString(), ".txt").toFile();
            fmFile.deleteOnExit();
            try (OutputStream os = new FileOutputStream(fmFile)) {
                os.write(featureMap.getBytes());
            }
            return fmFile;
        } catch (IOException e) {
            throw new RuntimeException("Cannot generate feature map file" , e);
        }
    }
    
    @Override
    public void update(int treeId) {
        updateTask = new XGBoostUpdateTask(setupTask, treeId);
        updateTask.run();
    }

    @Override
    public byte[] updateBooster() {
        latestBooster = updateTask.getBoosterBytes();
        return latestBooster;
    }

    @Override
    public Map<String, FeatureScore> getFeatureScores() {
        Booster booster = null;
        try {
            booster = BoosterHelper.loadModel(latestBooster);
            return BoosterHelper.doWithLocalRabit(booster1 -> {
                String fmPath = featureMapFile.getAbsolutePath();
                final String[] modelDump = booster1.getModelDump(fmPath, true);
                return XGBoostUtils.parseFeatureScores(modelDump);
            }, booster);
        } catch (XGBoostError e) {
            throw new RuntimeException("Failed to get feature scores.", e);
        } finally {
            if (booster != null)
                BoosterHelper.dispose(booster);
        }
    }

    @Override
    public void cleanup() {
        XGBoostCleanupTask.cleanUp(setupTask);
        stopRabitTracker();
        if (featureMapFile != null) {
            if (!featureMapFile.delete()) {
                Log.warn("Unable to delete file " + featureMapFile + ". Please do a manual clean-up.");
            }
        }
    }

}
