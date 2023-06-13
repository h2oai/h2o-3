package hex.tree.xgboost.exec;

import hex.CustomMetric;
import hex.DataInfo;
import hex.genmodel.utils.IOUtils;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.EvalMetric;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.matrix.FrameMatrixLoader;
import hex.tree.xgboost.matrix.MatrixLoader;
import hex.tree.xgboost.matrix.RemoteMatrixLoader;
import hex.tree.xgboost.rabit.RabitTrackerH2O;
import hex.tree.xgboost.task.XGBoostCleanupTask;
import hex.tree.xgboost.task.XGBoostSetupTask;
import hex.tree.xgboost.task.XGBoostUpdateTask;
import water.DKV;
import water.H2O;
import water.Key;
import water.Keyed;
import water.fvec.Frame;
import water.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static hex.tree.xgboost.remote.RemoteXGBoostUploadServlet.getCheckpointFile;

public class LocalXGBoostExecutor implements XGBoostExecutor {

    interface CheckpointProvider { byte[] get(); }

    public final Key modelKey;
    private final BoosterParms boosterParams;
    private final MatrixLoader loader;
    private final CheckpointProvider checkpointProvider;
    private final boolean[] nodes;
    private final String saveMatrixDirectory;
    private final RabitTrackerH2O rt;
    private final Key<Frame> toCleanUp;

    private XGBoostSetupTask setupTask;
    private XGBoostUpdateTask updateTask;
    private EvalMetric evalMetric;
    
    /**
     * Used when executing from a remote model
     */
    public LocalXGBoostExecutor(Key key, XGBoostExecReq.Init init) {
        modelKey = key;
        rt = setupRabitTracker(init.num_nodes);
        boosterParams = BoosterParms.fromMap(init.parms);
        nodes = new boolean[H2O.CLOUD.size()];
        for (int i = 0; i < init.num_nodes; i++) nodes[i] = init.nodes[i] != null;
        loader = new RemoteMatrixLoader(modelKey);
        toCleanUp = null;
        saveMatrixDirectory = init.save_matrix_path;
        checkpointProvider = () -> {
            if (!init.has_checkpoint) {
                return null;
            } else {
                File checkpointFile = getCheckpointFile(modelKey.toString());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (FileInputStream fis = new FileInputStream(checkpointFile)) {
                    IOUtils.copyStream(fis, bos);
                } catch (IOException e) {
                    throw new RuntimeException("Failed writing data to response.", e);
                } finally {
                    checkpointFile.delete();
                }
                return bos.toByteArray();
            }
        };
    }

    /**
     * Used when executing from a local model
     */
    public LocalXGBoostExecutor(XGBoostModel model, Frame train, Frame valid) {
        modelKey = model._key;
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        if (valid != null) {
            XGBoostSetupTask.FrameNodes validFrameNodes = XGBoostSetupTask.findFrameNodes(valid);
            if (!validFrameNodes.isSubsetOf(trainFrameNodes)) {
                Log.warn("Need to re-distribute the Validation Frame because it has data on nodes that " +
                        "don't have any data of the training matrix. This might impact runtime performance.");
                toCleanUp = Key.make();
                valid = train.makeSimilarlyDistributed(valid, toCleanUp);
            } else 
                toCleanUp = null;
        } else 
            toCleanUp = null;
        rt = setupRabitTracker(trainFrameNodes.getNumNodes());
        DataInfo dataInfo = model.model_info().dataInfo();
        boosterParams = XGBoostModel.createParams(model._parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = boosterParams.toTwoDimTable();
        loader = new FrameMatrixLoader(model, train, valid);
        nodes = trainFrameNodes._nodes;
        saveMatrixDirectory = model._parms._save_matrix_directory;
        checkpointProvider = () -> {
            if (model._parms.hasCheckpoint()) {
                return model.model_info()._boosterBytes;
            } else {
                return null;
            }
        };
    }

    @Override
    public byte[] setup() {
        setupTask = new XGBoostSetupTask(
            modelKey, saveMatrixDirectory, boosterParams, checkpointProvider.get(), getRabitEnv(), nodes, loader
        );
        setupTask.run();
        updateTask = new XGBoostUpdateTask(setupTask, 0).run();
        return updateTask.getBoosterBytes();
    }

    private RabitTrackerH2O setupRabitTracker(int numNodes) {
        // XGBoost seems to manipulate its frames in case of a 1 node distributed version in a way 
        // the GPU plugin can't handle Therefore don't use RabitTracker envs for 1 node
        if (numNodes > 1) {
            RabitTrackerH2O rt = new RabitTrackerH2O(numNodes);
            rt.start(0);
            return rt;
        } else {
            return null;
        }
    }

    private void stopRabitTracker() {
        if (rt != null) {
            rt.waitFor(0);
            rt.stop();
        }
    }

    private Map<String, String> getRabitEnv() {
        if (rt != null) {
            return rt.getWorkerEnvs();
        } else {
            return new HashMap<>();
        }
    }

    @Override
    public void update(int treeId) {
        evalMetric = null; // invalidate cached eval metric
        updateTask = new XGBoostUpdateTask(setupTask, treeId);
        updateTask.run();
    }

    @Override
    public EvalMetric getEvalMetric() {
        if (evalMetric != null) { // re-use cached value, this is important for early stopping when final scoring is done without prior boosting iteration
            return evalMetric;
        }
        if  (updateTask == null) {
            throw new IllegalStateException(
                    "Custom metric can only be retrieved immediately after running update iteration.");
        }
        evalMetric = updateTask.getEvalMetric();
        return evalMetric;
    }

    @Override
    public byte[] updateBooster() {
        if (updateTask != null) {
            byte[] booster = updateTask.getBoosterBytes();
            updateTask = null;
            return booster;
        }
        return null;
    }

    @Override
    public void close() {
        if (toCleanUp != null) {
            Keyed.remove(toCleanUp);
        }
        XGBoostCleanupTask.cleanUp(setupTask);
        stopRabitTracker();
    }

}
