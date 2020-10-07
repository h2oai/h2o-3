package hex.tree.xgboost.exec;

import hex.DataInfo;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.BoosterParms;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.task.XGBoostUploadMatrixTask;
import hex.tree.xgboost.task.XGBoostSetupTask;
import org.apache.log4j.Logger;
import water.H2O;
import water.Key;
import water.fvec.Frame;

import static hex.tree.xgboost.remote.RemoteXGBoostUploadServlet.RequestType.checkpoint;

public class RemoteXGBoostExecutor implements XGBoostExecutor {
    
    private static final Logger LOG = Logger.getLogger(RemoteXGBoostExecutor.class);

    public final XGBoostHttpClient http;
    public final Key modelKey;
    
    public RemoteXGBoostExecutor(XGBoostModel model, Frame train, String remoteUri, String userName, String password) {
        final boolean https = H2O.ARGS.jks != null;
        http = new XGBoostHttpClient(remoteUri, https, userName, password);
        modelKey = model._key;
        XGBoostExecReq.Init req = new XGBoostExecReq.Init();
        XGBoostSetupTask.FrameNodes trainFrameNodes = XGBoostSetupTask.findFrameNodes(train);
        req.num_nodes = trainFrameNodes.getNumNodes();
        DataInfo dataInfo = model.model_info().dataInfo();
        req.parms = XGBoostModel.createParamsMap(model._parms, model._output.nclasses(), dataInfo.coefNames());
        model._output._native_parameters = BoosterParms.fromMap(req.parms).toTwoDimTable();
        req.save_matrix_path = model._parms._save_matrix_directory;
        req.nodes = collectNodes(trainFrameNodes);
        LOG.info("Initializing remote executor.");
        XGBoostExecRespV3 resp = http.postJson(modelKey, "init", req);
        String[] remoteNodes = resp.readData();
        assert modelKey.equals(resp.key.key());
        uploadCheckpointBooster(model);
        uploadMatrices(model, train, trainFrameNodes, remoteNodes, https, remoteUri, userName, password);
        LOG.info("Remote executor init complete.");
    }

    private void uploadMatrices(
        XGBoostModel model, Frame train,
        XGBoostSetupTask.FrameNodes trainFrameNodes, String[] remoteNodes,
        boolean https, String leaderUri, String userName, String password
    ) {
        LOG.info("Starting matrix data upload.");
        new XGBoostUploadMatrixTask(
                model, train, trainFrameNodes._nodes, remoteNodes, 
                https, parseContextPath(leaderUri), userName, password
        ).run();
    }

    private String parseContextPath(String leaderUri) {
        int slashIndex = leaderUri.indexOf("/");
        if (slashIndex > 0) {
            return leaderUri.substring(slashIndex);
        } else {
            return "";
        }
    }

    private void uploadCheckpointBooster(XGBoostModel model) {
        if (!model._parms.hasCheckpoint()) {
            return;
        }
        LOG.info("Uploading booster checkpoint.");
        http.uploadBytes(modelKey, checkpoint, model.model_info()._boosterBytes);
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
        return http.downloadBytes(modelKey, "setup", req);
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
        return http.downloadBytes(modelKey, "getBooster", req);
    }

    @Override
    public void close() {
        XGBoostExecReq req = new XGBoostExecReq(); // no req params
        XGBoostExecRespV3 resp = http.postJson(modelKey, "cleanup", req);
        assert resp.key.key().equals(modelKey);
    }
}
