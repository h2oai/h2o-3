package hex.tree.xgboost.remote;

import hex.schemas.XGBoostExecReqV3;
import hex.tree.xgboost.exec.LocalXGBoostExecutor;
import water.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XGBoostExecutorRegistry {

    private static final Map<Key, LocalXGBoostExecutor> REGISTRY = new ConcurrentHashMap<>();

    public static LocalXGBoostExecutor getExecutor(XGBoostExecReqV3 req) {
        return REGISTRY.get(req.key.key());
    }

    public static void storeExecutor(LocalXGBoostExecutor exec) {
        REGISTRY.put(exec.modelKey, exec);
    }
    
    public static void removeExecutor(LocalXGBoostExecutor exec) {
        REGISTRY.remove(exec.modelKey);
    }

}
