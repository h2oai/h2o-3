package hex.tree.xgboost.exec;

import hex.DataInfo;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostUtils;
import hex.tree.xgboost.rabit.RabitTrackerH2O;
import hex.tree.xgboost.util.BoosterHelper;
import hex.tree.xgboost.util.FeatureScore;
import ml.dmlc.xgboost4j.java.*;
import water.H2O;
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
    
    public final XGBoostModel.XGBoostParameters parms;
    public final XGBoostModel model;
    public final Frame train;
    public final RabitTrackerH2O rt;
    public final XGBoostSetupTask.FrameNodes trainFrameNodes;
    
    private File featureMapFile;
    private DataInfo dataInfo;
    private XGBoostSetupTask setupTask;
    private BoosterProvider boosterProvider;

    public LocalXGBoostExecutor(XGBoostModel.XGBoostParameters parms, XGBoostModel model, Frame train) {
        this.parms = parms;
        this.model = model;
        this.train = train;
        // perform training only on a subset of nodes that have training frame data
        trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        rt = new RabitTrackerH2O(trainFrameNodes.getNumNodes());
    }

    @Override
    public XGBoostModel getModel() {
        return model;
    }

    @Override
    public void setup() {
        startRabitTracker();
        createFeatureMap();
        createSetupTask();
        XGBoostUpdateTask nullModelTask = new XGBoostUpdateTask(setupTask, 0).run();
        boosterProvider = new BoosterProvider(model.model_info(), featureMapFile, nullModelTask);
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
    private Map<String, String> getWorkerEnvs(RabitTrackerH2O rt) {
        if(H2O.CLOUD.size() > 1) {
            return rt.getWorkerEnvs();
        } else {
            return new HashMap<>();
        }
    }

    private void createFeatureMap() {
        // Create a "feature map" and store in a temporary file (for Variable Importance, MOJO, ...)
        dataInfo = model.model_info().dataInfo();
        assert dataInfo != null;
        String featureMap = XGBoostUtils.makeFeatureMap(train, dataInfo);
        model.model_info().setFeatureMap(featureMap);
        featureMapFile = createFeatureMapFile(featureMap);
    }

    // For feature importance - write out column info
    private File createFeatureMapFile(String featureMap) {
        try {
            File fmFile = Files.createTempFile("h2o_xgb_" + model._key.toString(), ".txt").toFile();
            fmFile.deleteOnExit();
            try (OutputStream os = new FileOutputStream(fmFile)) {
                os.write(featureMap.getBytes());
            }
            return fmFile;
        } catch (IOException e) {
            throw new RuntimeException("Cannot generate feature map file" , e);
        }
    }
    
    private void createSetupTask() {
        BoosterParms boosterParms = XGBoostModel.createParams(parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = boosterParms.toTwoDimTable();
        byte[] checkpointBytes = null;
        if (parms.hasCheckpoint()) {
            checkpointBytes = model.model_info()._boosterBytes;
        }
        setupTask = new LocalXGBoostSetupTask(model, parms, boosterParms, checkpointBytes, getWorkerEnvs(rt), trainFrameNodes).run();
    }

    @Override
    public void update(int treeId) {
        XGBoostUpdateTask t = new XGBoostUpdateTask(setupTask, treeId).run();
        boosterProvider.reset(t);
    }

    @Override
    public void updateBooster() {
        boosterProvider.updateBooster();
    }

    @Override
    public Map<String, FeatureScore> getFeatureScores() throws XGBoostError {
        Booster booster = null;
        try {
            booster = model.model_info().deserializeBooster();
            return BoosterHelper.doWithLocalRabit(booster1 -> {
                String fmPath = boosterProvider._featureMapFile.getAbsolutePath();
                final String[] modelDump = booster1.getModelDump(fmPath, true);
                return XGBoostUtils.parseFeatureScores(modelDump);
            }, booster);
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
            if (! featureMapFile.delete()) {
                Log.warn("Unable to delete file " + featureMapFile + ". Please do a manual clean-up.");
            }
        }
    }

}
