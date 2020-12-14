package hex.tree.xgboost.remote;

import hex.genmodel.utils.IOUtils;
import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.exec.LocalXGBoostExecutor;
import hex.tree.xgboost.exec.XGBoostExecReq;
import org.apache.log4j.Logger;
import water.H2O;
import water.api.Handler;
import water.api.StreamWriteOption;
import water.api.StreamWriter;
import water.api.StreamingSchema;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import static hex.tree.xgboost.remote.XGBoostExecutorRegistry.*;

public class RemoteXGBoostHandler extends Handler {

    private static final Logger LOG = Logger.getLogger(RemoteXGBoostHandler.class);

    private XGBoostExecRespV3 makeResponse(LocalXGBoostExecutor exec) {
        return new XGBoostExecRespV3(exec.modelKey);
    }

    @SuppressWarnings("unused")
    public XGBoostExecRespV3 init(int ignored, XGBoostExecReqV3 req) {
        XGBoostExecReq.Init init = req.readData();
        LocalXGBoostExecutor exec = new LocalXGBoostExecutor(req.key.key(), init);
        storeExecutor(exec);
        return new XGBoostExecRespV3(exec.modelKey, collectNodes());
    }
    
    private final String[] collectNodes() {
        String[] nodes = new String[H2O.CLOUD.size()];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = H2O.CLOUD.members()[i].getIpPortString();
        }
        return nodes;
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
        XGBoostExecReq.Update update = req.readData();
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
        removeExecutor(exec);
        return makeResponse(exec);
    }

    private StreamingSchema streamBytes(byte[] data) {
        final byte[] dataToSend;
        if (data == null) dataToSend = new byte[0];
        else dataToSend = data;
        return new StreamingSchema((os, options) -> {
          try { 
              IOUtils.copyStream(new ByteArrayInputStream(dataToSend), os);
          } catch (IOException e) { 
              LOG.error("Failed writing data to response.", e);
              throw new RuntimeException("Failed writing data to response.", e);
          }  
        });
    }


}
