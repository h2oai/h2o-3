package hex.tree.xgboost;

import hex.CVModelBuilder;
import hex.ModelBuilder;
import hex.tree.xgboost.util.GpuUtils;
import org.apache.log4j.Logger;
import water.Job;

import java.util.*;

public class XGBoostGPUCVModelBuilder extends CVModelBuilder {

    private static final Logger LOG = Logger.getLogger(XGBoostGPUCVModelBuilder.class);

    private final Deque<Integer> availableGpus;

    public XGBoostGPUCVModelBuilder(
        Job job,
        ModelBuilder<?, ?, ?>[] modelBuilders,
        int parallelization,
        int[] gpuIds
    ) {
        super(job, modelBuilders, parallelization);
        if (gpuIds != null && gpuIds.length > 0) {
            availableGpus = new LinkedList<>();
            for (int id : gpuIds) availableGpus.add(id);
        } else {
            availableGpus = new LinkedList<>(GpuUtils.allGPUs());
        }
        LOG.info("Using parallel GPU building on " + availableGpus.size() + " GPUs.");
    }

    @Override
    protected void prepare(ModelBuilder m) {
        XGBoost xgb = (XGBoost) m;
        xgb._parms._gpu_id = new int[] { availableGpus.pop() };
        LOG.info("Building " + xgb.dest() + " on GPU " + xgb._parms._gpu_id[0]);
    }

    @Override
    protected void finished(ModelBuilder m) {
        XGBoost xgb = (XGBoost) m;
        availableGpus.push(xgb._parms._gpu_id[0]);
    }

}
