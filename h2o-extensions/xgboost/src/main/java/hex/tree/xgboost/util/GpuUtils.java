package hex.tree.xgboost.util;

import ai.h2o.xgboost4j.java.*;
import org.apache.log4j.Logger;
import water.DTask;
import water.H2O;
import water.H2ONode;
import water.RPC;

import java.io.IOException;
import java.util.*;

public class GpuUtils {

    private static final Logger LOG = Logger.getLogger(GpuUtils.class);
    
    public static final int[] DEFAULT_GPU_ID = new int[] { 0 };

    private static volatile boolean defaultGpuIdNotValid = false;
    private static volatile boolean gpuSearchPerformed = false;

    private static final Set<Integer> GPUS = new HashSet<>();

    static boolean isGpuSupportEnabled() {
        try {
            INativeLibLoader loader = NativeLibLoader.getLoader();
            if (!(loader instanceof NativeLibraryLoaderChain))
                return false;
            NativeLibraryLoaderChain chainLoader = (NativeLibraryLoaderChain) loader;
            NativeLibrary lib = chainLoader.getLoadedLibrary();
            return lib.hasCompilationFlag(NativeLibrary.CompilationFlags.WITH_GPU);
        } catch (IOException e) {
            LOG.debug(e);
            return false;
        }
    }

    private static boolean gpuCheckEnabled() {
        return H2O.getSysBoolProperty("xgboost.gpu.check.enabled", true);
    }

    public static int numGPUs(H2ONode node) {
        return allGPUs(node).size();
    }

    public static Set<Integer> allGPUs(H2ONode node) {
        if (H2O.SELF.equals(node)) {
            return allGPUs();
        } else {
            AllGPUsTask t = new AllGPUsTask();
            new RPC<>(node, t).call().get();
            return new HashSet<>(Arrays.asList(t.gpuIds));
        }
    }

    private static class AllGPUsTask extends DTask<HasGPUTask> {
        // OUT
        private Integer[] gpuIds;

        private AllGPUsTask() {
        }

        @Override
        public void compute2() {
            gpuIds = allGPUs().toArray(new Integer[0]);
            tryComplete();
        }
    }

    public static Set<Integer> allGPUs() {
        if (gpuSearchPerformed) return Collections.unmodifiableSet(GPUS);
        int nextGpuId = 0;
        while (hasGPU(new int[] { nextGpuId })) {
            nextGpuId++;
        }
        gpuSearchPerformed = true;
        return Collections.unmodifiableSet(GPUS);
    }

    public static boolean hasGPU(H2ONode node, int[] gpu_id) {
        final boolean hasGPU;
        if (H2O.SELF.equals(node)) {
            hasGPU = hasGPU(gpu_id);
        } else {
            HasGPUTask t = new HasGPUTask(gpu_id);
            new RPC<>(node, t).call().get();
            hasGPU = t._hasGPU;
        }
        LOG.debug("Availability of GPU (id=" + Arrays.toString(gpu_id) + ") on node " + node + ": " + hasGPU);
        return hasGPU;
    }

    private static class HasGPUTask extends DTask<HasGPUTask> {
        private final int[] _gpu_id;
        // OUT
        private boolean _hasGPU;

        private HasGPUTask(int[] gpu_id) {
            _gpu_id = gpu_id;
        }

        @Override
        public void compute2() {
            _hasGPU = hasGPU(_gpu_id);
            tryComplete();
        }
    }

    public static boolean hasGPU(int[] gpu_id) {
        if (!gpuCheckEnabled()) {
            return true;
        }
        if (gpu_id == null && defaultGpuIdNotValid) // quick default path & no synchronization - if we already know we don't have the default GPU, let's not to find out again
            return false;
        boolean hasGPU = true;
        if (gpu_id == null) gpu_id = DEFAULT_GPU_ID;
        for (int i = 0; hasGPU && i < gpu_id.length; i++) {
            hasGPU = hasGPU_impl(gpu_id[i]);
        }
        if (Arrays.equals(gpu_id, DEFAULT_GPU_ID) && !hasGPU) {
            defaultGpuIdNotValid = true; // this can never change back
        }
        return hasGPU;
    }

    public static boolean hasGPU() {
        return hasGPU(null);
    }

    // helper
    private static synchronized boolean hasGPU_impl(int gpu_id) {
        if (!isGpuSupportEnabled()) {
            return false;
        }

        if (GPUS.contains(gpu_id)) {
            return true;
        }

        DMatrix trainMat;
        try {
            trainMat = new DMatrix(new float[]{1, 2, 1, 2}, 2, 2);
            trainMat.setLabel(new float[]{1, 0});
        } catch (XGBoostError xgBoostError) {
            throw new IllegalStateException("Couldn't prepare training matrix for XGBoost.", xgBoostError);
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put("tree_method", "gpu_hist");
        params.put("silent", 1);
        params.put("fail_on_invalid_gpu_id", true);

        params.put("gpu_id", gpu_id);
        HashMap<String, DMatrix> watches = new HashMap<>();
        watches.put("train", trainMat);
        try {
            Map<String, String> localRabitEnv = new HashMap<>();
            Rabit.init(localRabitEnv);
            ai.h2o.xgboost4j.java.XGBoost.train(trainMat, params, 1, watches, null, null);
            GPUS.add(gpu_id);
            return true;
        } catch (XGBoostError xgBoostError) {
            return false;
        } finally {
            try {
                Rabit.shutdown();
            } catch (XGBoostError e) {
                LOG.warn("Cannot shutdown XGBoost Rabit for current thread.");
            }
        }
    }

}
