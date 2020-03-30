package hex.tree.xgboost.remote;

import hex.schemas.XGBoostExecReqV3;
import hex.schemas.XGBoostExecRespV3;
import hex.tree.xgboost.exec.LocalXGBoostExecutor;
import hex.tree.xgboost.exec.XGBoostExecReq;
import org.apache.commons.codec.binary.Base64;
import water.AutoBuffer;
import water.Key;
import water.api.Handler;

import java.util.HashMap;
import java.util.Map;

public class RemoteXGBoostHandler extends Handler {
    
    private static final Map<Key, LocalXGBoostExecutor> REGISTRY = new HashMap<>();

    public XGBoostExecRespV3 exec(int ignored, XGBoostExecReqV3 req) {
        switch (req.type) {
            case INIT: return init(req);
            case SETUP: return setup(REGISTRY.get(req.key.key()));
            case UPDATE: return update(REGISTRY.get(req.key.key()), req);
            case BOOSTER: return booster(REGISTRY.get(req.key.key()));
            case FEATURES: return features(REGISTRY.get(req.key.key()));
            case CLEANUP: return cleanup(REGISTRY.get(req.key.key()));
            default: throw new IllegalArgumentException("Unexpected request type");
        }
    }

    private XGBoostExecRespV3 makeResponse(LocalXGBoostExecutor exec) {
        return new XGBoostExecRespV3(exec.modelKey);
    }

    private XGBoostExecRespV3 init(XGBoostExecReqV3 req) {
        XGBoostExecReq.Init init = (XGBoostExecReq.Init) AutoBuffer.javaSerializeReadPojo(Base64.decodeBase64(req.data));
        LocalXGBoostExecutor exec = new LocalXGBoostExecutor(req.key.key(), init);
        REGISTRY.put(exec.modelKey, exec);
        return makeResponse(exec);
    }

    private XGBoostExecRespV3 setup(LocalXGBoostExecutor exec) {
        byte[] booster = exec.setup();
        return new XGBoostExecRespV3(exec.modelKey, booster);
    }

    private XGBoostExecRespV3 update(LocalXGBoostExecutor exec, XGBoostExecReqV3 req) {
        XGBoostExecReq.Update update = (XGBoostExecReq.Update) AutoBuffer.javaSerializeReadPojo(Base64.decodeBase64(req.data));
        exec.update(update.treeId);
        return makeResponse(exec);
    }

    private XGBoostExecRespV3 booster(LocalXGBoostExecutor exec) {
        byte[] booster = exec.updateBooster();
        return new XGBoostExecRespV3(exec.modelKey, booster);
    }

    private XGBoostExecRespV3 features(LocalXGBoostExecutor exec) {
        Object res = exec.getFeatureScores();
        return new XGBoostExecRespV3(exec.modelKey, AutoBuffer.javaSerializeWritePojo(res));
    }

    private XGBoostExecRespV3 cleanup(LocalXGBoostExecutor exec) {
        exec.cleanup();
        REGISTRY.remove(exec.modelKey);
        return makeResponse(exec);
    }

}
