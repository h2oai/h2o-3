package hex.tree.xgboost;

import hex.CVModelBuilder;
import hex.ModelBuilder;
import hex.tree.xgboost.util.GpuUtils;
import org.apache.log4j.Logger;
import water.Job;

import java.util.*;

public class XGBoostGPUCVModelBuilder extends CVModelBuilder {

    private static final Logger LOG = Logger.getLogger(XGBoostGPUCVModelBuilder.class);

    private final int[] _gpu_utilization;

    public XGBoostGPUCVModelBuilder(
        Job job,
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
        LOG.info("Using parallel GPU building on " + availableGpus.size() + " GPUs.");
        _gpu_utilization = initUtilization(availableGpus);
    }

    static int[] initUtilization(List<Integer> gpus) {
        final int maxGpuId = gpus.stream().max(Integer::compareTo)
                .orElseThrow(() -> new IllegalStateException("There are no GPUs available for XGBoost (" + gpus + ")."));
        final int[] utilization = new int[maxGpuId + 1];
        Arrays.fill(utilization, -1);
        gpus.forEach(id -> utilization[id] = 0);
        return utilization;
    }

    @Override
    protected void prepare(ModelBuilder<?, ?, ?> m) {
        XGBoost xgb = (XGBoost) m;
        xgb._parms._gpu_id = new int[] { takeLeastUtilizedGPU() };
        LOG.info("Building " + xgb.dest() + " on GPU " + xgb._parms._gpu_id[0]);
    }

    @Override
    protected void finished(ModelBuilder<?, ?, ?> m) {
        XGBoost xgb = (XGBoost) m;
        returnGPU(xgb._parms._gpu_id[0]);
    }

    private void returnGPU(int id) {
        _gpu_utilization[id]--;
    }

    private int takeLeastUtilizedGPU() {
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
