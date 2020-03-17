package hex.tree.xgboost.exec;

import hex.DataInfo;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.matrix.FrameMatrixLoader;
import hex.tree.xgboost.matrix.MatrixLoader;
import hex.tree.xgboost.task.XGBoostSaveMatrixTask;
import hex.tree.xgboost.task.XGBoostSetupTask;
import water.H2O;
import water.Key;
import water.fvec.Frame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class RemoteXGBoostExecutor implements XGBoostExecutor {

    public final XGBoostHttpClient http;
    public final Key modelKey;
    
    public RemoteXGBoostExecutor(String remoteUri, XGBoostModel model, Frame train) {
        http = new XGBoostHttpClient(remoteUri);
        modelKey = model._key;
        XGBoostExecReq.Init req = new XGBoostExecReq.Init();
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        req.num_nodes = trainFrameNodes.getNumNodes();
        DataInfo dataInfo = model.model_info().dataInfo();
        req.parms = XGBoostModel.createParamsMap(model._parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = BoosterParms.fromMap(req.parms).toTwoDimTable();
        MatrixLoader loader = new FrameMatrixLoader(model, train);
        req.matrix_dir_path = H2O.ICE_ROOT.toString() + "/" + modelKey.toString();
        req.save_matrix_path = model._parms._save_matrix_directory;
        req.nodes = collectNodes(trainFrameNodes);
        new XGBoostSaveMatrixTask(modelKey, req.matrix_dir_path, trainFrameNodes._nodes, loader).run();
        if (model._parms.hasCheckpoint()) {
            req.has_checkpoint = true;
            saveCheckpointBoosterToFile(model, req.matrix_dir_path);
        }
        XGBoostExecRespV3 resp = http.postJson(modelKey, "init", req);
        assert modelKey.equals(resp.key.key());
    }

    private void saveCheckpointBoosterToFile(XGBoostModel model, String matrix_dir_path) {
        File checkpointFile = new File(matrix_dir_path, "checkpoint.bin");
        try (FileOutputStream fos = new FileOutputStream(checkpointFile)) {
            fos.write(model.model_info()._boosterBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write checkpoint into file.", e);
        }
    }

    private String[] collectNodes(XGBoostSetupTask.FrameNodes nodes) {
        String[] res = new String[H2O.CLOUD.size()];
        for (int i = 0; i < nodes._nodes.length; i++) {
            if (nodes._nodes[i]) {
                res[i] = H2O.CLOUD.members()[i].getIpPortString();
            }
        }
        return res;
    }

    @Override
    public byte[] setup() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        return http.postBytes(modelKey, "setup", req);
    }

    @Override
    public void update(int treeId) {
        XGBoostExecReq.Update req = new XGBoostExecReq.Update();
        req.treeId = treeId;
        XGBoostExecRespV3 resp = http.postJson(modelKey, "update", req);
        assert resp.key.key().equals(modelKey);
    }

    @Override
    public byte[] updateBooster() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        return http.postBytes(modelKey, "getBooster", req);
    }

    @Override
    public void close() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        XGBoostExecRespV3 resp = http.postJson(modelKey, "cleanup", req);
        assert resp.key.key().equals(modelKey);
    }
}
