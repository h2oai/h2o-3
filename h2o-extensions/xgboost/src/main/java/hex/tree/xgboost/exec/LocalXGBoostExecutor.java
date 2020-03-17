package hex.tree.xgboost.exec;

import hex.DataInfo;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.matrix.FrameMatrixLoader;
import hex.tree.xgboost.matrix.MatrixLoader;
import hex.tree.xgboost.matrix.RemoteMatrixLoader;
import hex.tree.xgboost.rabit.RabitTrackerH2O;
import hex.tree.xgboost.task.XGBoostCleanupTask;
import hex.tree.xgboost.task.XGBoostSetupTask;
import hex.tree.xgboost.task.XGBoostUpdateTask;
import water.H2O;
import water.Key;
import water.fvec.Frame;

import java.util.HashMap;
import java.util.Map;

public class LocalXGBoostExecutor implements XGBoostExecutor {

    public final Key modelKey;
    private final RabitTrackerH2O rt;
    private final XGBoostSetupTask setupTask;
    
    private XGBoostUpdateTask updateTask;
    
    /**
     * Used when executing from a remote model
     */
    public LocalXGBoostExecutor(Key key, XGBoostExecReq.Init init) {
        modelKey = key;
        rt = setupRabitTracker(init.num_nodes);
        BoosterParms boosterParams = BoosterParms.fromMap(init.parms);
        boolean[] nodes = new boolean[H2O.CLOUD.size()];
        for (int i = 0; i < init.num_nodes; i++) nodes[i] = init.nodes[i] != null;
        MatrixLoader loader = new RemoteMatrixLoader(init.matrix_dir_path, init.nodes);
        byte[] checkpoint = null;
        if (init.has_checkpoint) {
            XGBoostHttpClient http = new XGBoostHttpClient(init.nodes[0]);
            XGBoostExecReq.GetCheckPoint req = new XGBoostExecReq.GetCheckPoint();
            req.matrix_dir_path = init.matrix_dir_path;
            checkpoint = http.postBytes(null, "getCheckpoint", req);
        }
        setupTask = new XGBoostSetupTask(
            modelKey, init.save_matrix_path, boosterParams, checkpoint, getRabitEnv(), nodes, loader
        );
    }

    /**
     * Used when executing from a local model
     */
    public LocalXGBoostExecutor(XGBoostModel model, Frame train) {
        modelKey = model._key;
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        rt = setupRabitTracker(trainFrameNodes.getNumNodes());
        byte[] checkpointBytes = null;
        if (model._parms.hasCheckpoint()) {
            checkpointBytes = model.model_info()._boosterBytes;
        }
        DataInfo dataInfo = model.model_info().dataInfo();
        BoosterParms boosterParms = XGBoostModel.createParams(model._parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = boosterParms.toTwoDimTable();
        MatrixLoader loader = new FrameMatrixLoader(model, train);
        setupTask = new XGBoostSetupTask(
            modelKey, model._parms._save_matrix_directory, boosterParms, checkpointBytes, 
            getRabitEnv(), trainFrameNodes._nodes, loader
        );
    }
    
    @Override
    public byte[] setup() {
        setupTask.run();
        updateTask = new XGBoostUpdateTask(setupTask, 0).run();
        return updateTask.getBoosterBytes();
    }

    private RabitTrackerH2O setupRabitTracker(int numNodes) {
        // XGBoost seems to manipulate its frames in case of a 1 node distributed version in a way 
        // the GPU plugin can't handle Therefore don't use RabitTracker envs for 1 node
        if (H2O.CLOUD.size() > 1) {
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
        updateTask = new XGBoostUpdateTask(setupTask, treeId);
        updateTask.run();
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
        XGBoostCleanupTask.cleanUp(setupTask);
        stopRabitTracker();
    }

}
