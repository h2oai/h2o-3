package hex.tree.xgboost.remote;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.exec.LocalXGBoostExecutor;
import hex.tree.xgboost.exec.XGBoostExecReq;
import hex.tree.xgboost.task.XGBoostSaveMatrixTask;
import org.apache.log4j.Logger;
import water.Key;
import water.api.Handler;
import water.api.StreamingSchema;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RemoteXGBoostHandler extends Handler {

    private static final Logger LOG = Logger.getLogger(RemoteXGBoostHandler.class);

    private static final Map<Key, LocalXGBoostExecutor> REGISTRY = new HashMap<>();

    private XGBoostExecRespV3 makeResponse(LocalXGBoostExecutor exec) {
        return new XGBoostExecRespV3(exec.modelKey);
    }
    
    private LocalXGBoostExecutor getExecutor(XGBoostExecReqV3 req) {
        return REGISTRY.get(req.key.key());
    }

    @SuppressWarnings("unused")
    public XGBoostExecRespV3 init(int ignored, XGBoostExecReqV3 req) {
        XGBoostExecReq.Init init = req.readReq();
        LocalXGBoostExecutor exec = new LocalXGBoostExecutor(req.key.key(), init);
        REGISTRY.put(exec.modelKey, exec);
        return makeResponse(exec);
    }

    @SuppressWarnings("unused")
    public StreamingSchema setup(int ignored, XGBoostExecReqV3 req) {
        LocalXGBoostExecutor exec = getExecutor(req);
        byte[] booster = exec.setup();
        return streamBytes(booster);
    }

    @SuppressWarnings("unused")
    public XGBoostExecRespV3 update(int ignored, XGBoostExecReqV3 req) {
        LocalXGBoostExecutor exec = getExecutor(req);
        XGBoostExecReq.Update update = req.readReq();
        exec.update(update.treeId);
        return makeResponse(exec);
    }

    @SuppressWarnings("unused")
    public StreamingSchema getBooster(int ignored, XGBoostExecReqV3 req) {
        LocalXGBoostExecutor exec = getExecutor(req);
        byte[] booster = exec.updateBooster();
        return streamBytes(booster);
    }

    @SuppressWarnings("unused")
    public XGBoostExecRespV3 cleanup(int ignored, XGBoostExecReqV3 req) {
        LocalXGBoostExecutor exec = getExecutor(req);
        exec.close();
        REGISTRY.remove(exec.modelKey);
        return makeResponse(exec);
    }

    @SuppressWarnings("unused")
    public StreamingSchema getMatrix(int ignored, XGBoostExecReqV3 req) {
        XGBoostExecReq.GetMatrix matrix = req.readReq();
        File matrixFile = XGBoostSaveMatrixTask.getMatrixFile(new File(matrix.matrix_dir_path));
        return streamFile(matrixFile);
    }

    @SuppressWarnings("unused")
    public StreamingSchema getCheckpoint(int ignored, XGBoostExecReqV3 req) {
        XGBoostExecReq.GetCheckPoint checkPoint = req.readReq();
        File checkpointFile = new File(checkPoint.matrix_dir_path, "checkpoint.bin");
        return streamFile(checkpointFile);
    }
    
    private StreamingSchema streamFile(File file) {
        return new StreamingSchema(os -> {
            LOG.debug("Serving up data file " + file);
            try (FileInputStream fos = new FileInputStream(file)) {
                IOUtils.copyStream(fos, os);
            } catch (IOException e) {
                LOG.error("Failed writing data to response.", e);
                throw new RuntimeException("Failed writing data to response.", e);
            } finally {
                LOG.debug("Deleting data file " + file);
                file.delete();
            }
        });
    }

    private StreamingSchema streamBytes(byte[] data) {
        final byte[] dataToSend;
        if (data == null) dataToSend = new byte[0];
        else dataToSend = data;
        return new StreamingSchema(os -> {
            try {
                IOUtils.copyStream(new ByteArrayInputStream(dataToSend), os);
            } catch (IOException e) {
                LOG.error("Failed writing data to response.", e);
                throw new RuntimeException("Failed writing data to response.", e);
            }
        });
    }


}
