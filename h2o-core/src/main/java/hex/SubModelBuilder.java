package hex;

import org.apache.log4j.Logger;
import water.H2O;
import water.Job;
import water.ParallelizationTask;
import water.util.Log;

/**
 * Execute build of a collection of sub-models (CV models, main model) in parallel
 * 
 * This class is conceptually similar to CVModelBuilder and follows the same API. As opposed to CVModelBuilder
 * it is not limited to building just CV models but can built a mixture of CV and main models.
 * It also uses more efficient technique of model parallelization that works better when different sub-models
 * (eg. CV folds) take vastly different time to complete.
 * 
 * It currently lacks prepare/finished feature of CVModelBuilder
 */
public class SubModelBuilder {

    private static final Logger LOG = Logger.getLogger(SubModelBuilder.class);

    private final Job<?> job;
    private final ModelBuilder<?, ?, ?>[] modelBuilders;
    private final int parallelization;

    /**
     * @param job             parent job (processing will be stopped if stop of a parent job was requested)
     * @param modelBuilders   list of model builders to run in bulk
     * @param parallelization level of parallelization (how many models can be built at the same time)
     */
    public SubModelBuilder(
            Job<?> job, ModelBuilder<?, ?, ?>[] modelBuilders, int parallelization
    ) {
        this.job = job;
        this.modelBuilders = modelBuilders;
        this.parallelization = parallelization;
    }

    public void bulkBuildModels() {
        TrainModelTask[] tasks = new TrainModelTask[modelBuilders.length];
        for (int i = 0; i < modelBuilders.length; i++) {
            tasks[i] = new TrainModelTask(modelBuilders[i]);
        }
        H2O.submitTask(new ParallelizationTask<>(tasks, parallelization, job)).join();
    }

    private static class TrainModelTask extends H2O.H2OCountedCompleter<TrainModelTask> {
        private final ModelBuilder<?, ?, ?> _mb;

        TrainModelTask(ModelBuilder<?, ?, ?> mb) {
            _mb = mb;
        }

        @Override
        public void compute2() {
            LOG.info("Building " + _mb._desc + ".");
            boolean success = false;
            try {
                _mb.startClock();
                _mb.submitTrainModelTask().join();
                success = true;
            } finally {
                LOG.info(_mb._desc + (success ? " completed successfully." : " failed."));
            }
            tryComplete();
        }
    }

}
