package hex;

import org.apache.log4j.Logger;
import hex.ModelBuilder.TrainModelTaskController;
import water.Job;

/**
 * Execute Cross-Validation model build in parallel
 */
public class CVModelBuilder {
    
    private static final Logger LOG = Logger.getLogger(CVModelBuilder.class);

    private final Job job;
    private final ModelBuilder<?, ?, ?>[] modelBuilders;
    private final int parallelization;

    /**
     * @param job             parent job (processing will be stopped if stop of a parent job was requested)
     * @param modelBuilders   list of model builders to run in bulk
     * @param parallelization level of parallelization (how many models can be built at the same time)
     */
    public CVModelBuilder(
        Job job, ModelBuilder<?, ?, ?>[] modelBuilders, int parallelization
    ) {
        this.job = job;
        this.modelBuilders = modelBuilders;
        this.parallelization = parallelization;
    }
    
    protected void prepare(ModelBuilder<?, ?, ?> m) {}
    
    protected void finished(ModelBuilder<?, ?, ?> m) {}

    public void bulkBuildModels() {
        final int N = modelBuilders.length;
        TrainModelTaskController[] submodel_tasks = new TrainModelTaskController[N];
        int nRunning = 0;
        RuntimeException rt = null;
        for (int i = 0; i < N; ++i) {
            if (job.stop_requested()) {
                LOG.info("Skipping build of last " + (N - i) + " out of " + N + " cross-validation models");
                stopAll(submodel_tasks);
                throw new Job.JobCancelledException(job);
            }
            LOG.info("Building cross-validation model " + (i + 1) + " / " + N + ".");
            prepare(modelBuilders[i]);
            modelBuilders[i].startClock();
            submodel_tasks[i] = modelBuilders[i].submitTrainModelTask();
            if (++nRunning == parallelization) { //piece-wise advance in training the models
                while (nRunning > 0) {
                    final int waitForTaskIndex = i + 1 - nRunning;
                    try {
                        submodel_tasks[waitForTaskIndex].join();
                        finished(modelBuilders[waitForTaskIndex]);
                    } catch (RuntimeException t) {
                        if (rt == null) {
                            LOG.info("Exception from CV model #" + waitForTaskIndex + " will be reported as main exception.");
                            rt = t;
                        } else {
                            LOG.warn("CV model #" + waitForTaskIndex + " failed, the exception will not be reported", t);
                        }
                    } finally {
                        LOG.info("Completed cross-validation model " + waitForTaskIndex + " / " + N + ".");
                        nRunning--; // need to decrement regardless even if there is an exception, otherwise looping...
                    }
                }
                if (rt != null) throw rt;
            }
        }
        for (int i = 0; i < N; ++i) //all sub-models must be completed before the main model can be built
            try {
                final TrainModelTaskController task = submodel_tasks[i];
                assert task != null;
                task.join();
            } catch (RuntimeException t) {
                if (rt == null) {
                    LOG.info("Exception from CV model #" + i + " will be reported as main exception.");
                    rt = t;
                } else {
                    LOG.warn("CV model #" + i + " failed, the exception will not be reported", t);
                }
            } finally {
                LOG.info("Completed cross-validation model " + i + " / " + N + ".");
            }
        if (rt != null) throw rt;
    }

    private void stopAll(TrainModelTaskController[] tasks) {
        for (TrainModelTaskController task : tasks) {
            if (task != null) {
                task.cancel(true);
            }
        }
    }

    protected Job getJob() {
        return job;
    }
    
}
