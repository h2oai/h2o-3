package hex.tree.xgboost;

import java.util.HashMap;
import java.util.Map;

public class XGBoostGPULock {

    public static final Map<Integer, XGBoostGPULock> LOCKS = new HashMap<>();

    public static XGBoostGPULock lock(int gpuId) {
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
