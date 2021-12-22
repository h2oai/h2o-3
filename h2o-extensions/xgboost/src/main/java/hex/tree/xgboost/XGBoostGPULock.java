package hex.tree.xgboost;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static hex.tree.xgboost.util.GpuUtils.DEFAULT_GPU_ID;

class XGBoostGPULock {

    private static final Map<Integer, ReentrantLock> LOCKS = new HashMap<>();

    static int[] lock(int[] gpuIds) {
        if (gpuIds == null) {
            gpuIds = DEFAULT_GPU_ID;
        }
        initLocks(gpuIds);
        for (int id : gpuIds) {
            LOCKS.get(id).lock();
        }
        return gpuIds;
    }

    static void unlock(int[] gpuIds) {
        for (int id : gpuIds) {
            LOCKS.get(id).unlock();
        }
    }

    private static void initLocks(int[] gpuIds) {
        boolean allPresent = true;
        for (int id : gpuIds) {
            if (!LOCKS.containsKey(id)) {
                allPresent = false;
                break;
            }
        }
        if (!allPresent) {
            synchronized (LOCKS) {
                for (int id : gpuIds) {
                    if (!LOCKS.containsKey(id)) {
                        LOCKS.put(id, new ReentrantLock());
                    }
                }
            }
        }
    }

}
