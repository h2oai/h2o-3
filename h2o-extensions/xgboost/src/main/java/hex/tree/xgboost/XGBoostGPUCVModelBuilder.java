package hex.tree.xgboost;

import hex.CVModelBuilder;
import hex.ModelBuilder;
import hex.tree.xgboost.util.GpuUtils;
import org.apache.log4j.Logger;
import water.Job;

import java.util.*;

public class XGBoostGPUCVModelBuilder extends CVModelBuilder {

    private static final Logger LOG = Logger.getLogger(XGBoostGPUCVModelBuilder.class);

    private final GPUAllocator _allocator;
    
    public XGBoostGPUCVModelBuilder(
        Job<?> job,
        ModelBuilder<?, ?, ?>[] modelBuilders,
        int parallelization,
        int[] gpuIds
    ) {
        super(job, modelBuilders, parallelization);
        final List<Integer> availableGpus;
        if (gpuIds != null && gpuIds.length > 0) {
            availableGpus = new LinkedList<>();
            for (int id : gpuIds) availableGpus.add(id);
        } else {
            availableGpus = new LinkedList<>(GpuUtils.allGPUs());
        }
        LOG.info("Available #GPUs for CV model training: " + availableGpus.size());
        _allocator = new GPUAllocator(availableGpus);
    }

    @Override
    protected void prepare(ModelBuilder<?, ?, ?> m) {
        XGBoost xgb = (XGBoost) m;
        xgb._parms._gpu_id = new int[] { _allocator.takeLeastUtilizedGPU() };
        LOG.info("Building " + xgb.dest() + " on GPU " + xgb._parms._gpu_id[0]);
    }

    @Override
    protected void finished(ModelBuilder<?, ?, ?> m) {
        XGBoost xgb = (XGBoost) m;
        _allocator.releaseGPU(xgb._parms._gpu_id[0]);
    }

    static class GPUAllocator {
        final int[] _gpu_utilization;

        GPUAllocator(List<Integer> gpuIds) {
            this(initUtilization(gpuIds));
        }

        GPUAllocator(int[] gpuUtilization) {
            _gpu_utilization = gpuUtilization;
        }
        
        static int[] initUtilization(List<Integer> gpus) {
            final int maxGpuId = gpus.stream().max(Integer::compareTo)
                    .orElseThrow(() -> new IllegalStateException("There are no GPUs available for XGBoost (" + gpus + ")."));
            final int[] utilization = new int[maxGpuId + 1];
            Arrays.fill(utilization, -1);
            gpus.forEach(id -> utilization[id] = 0);
            return utilization;
        }

        void releaseGPU(int id) {
            _gpu_utilization[id]--;
        }

        int takeLeastUtilizedGPU() {
            int id = -1;
            for (int i = 0; i < _gpu_utilization.length; i++) {
                if (_gpu_utilization[i] == -1)
                    continue;
                if ((id == -1) || (_gpu_utilization[i] < _gpu_utilization[id])) {
                    id = i;
                }
            }
            assert id != -1;
            _gpu_utilization[id]++;
            return id;
        }

    }

}
