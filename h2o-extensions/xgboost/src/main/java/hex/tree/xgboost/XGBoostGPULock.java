package hex.tree.xgboost;

import java.util.HashMap;
import java.util.Map;

class XGBoostGPULock {

    private static final Map<Integer, XGBoostGPULock> LOCKS = new HashMap<>();

    static XGBoostGPULock lock(int gpuId) {
        if(!LOCKS.containsKey(gpuId)) {
            synchronized (XGBoostGPULock.class) {
                if(!LOCKS.containsKey(gpuId)) {
                    LOCKS.put(gpuId, new XGBoostGPULock());
                }
            }
        }
        return LOCKS.get(gpuId);
    }

}
