package hex.tree.xgboost;

import hex.BulkModelBuilder;
import hex.ModelBuilder;
import hex.tree.xgboost.util.GpuUtils;
import org.apache.log4j.Logger;
import water.Job;

import javax.sound.sampled.Line;
import java.util.*;

public class XGBoostGPUBulkModelBuilder extends BulkModelBuilder {

    private static final Logger LOG = Logger.getLogger(XGBoostGPUBulkModelBuilder.class);

    private final Deque<Integer> availableGpus;

    public XGBoostGPUBulkModelBuilder(
        String modelType,
        Job job,
        ModelBuilder<?, ?, ?>[] modelBuilders,
        int parallelization,
        int updateInc,
        int[] gpuIds
    ) {
        super(modelType, job, modelBuilders, parallelization, updateInc);
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
