package ai.h2o.automl;

import ai.h2o.automl.AutoML.Constraint;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.leaderboard.Leaderboard;
import hex.Model;
import hex.ModelContainer;
import water.Iced;
import water.Job;
import water.Key;
import water.util.ArrayUtils;
import water.util.Countdown;
import water.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Class responsible for starting all the {@link ModelingStep}s and monitoring their associated {@link Job},
 * i.e. polling jobs and adding their result model(s) to the {@link Leaderboard}.
 */
class ModelingStepsExecutor extends Iced<ModelingStepsExecutor> {

    static final int DEFAULT_POLLING_INTERVAL_IN_MILLIS = 1000;
    
    static void ensureStopRequestPropagated(Job job, Job parentJob, int pollingIntervalInMillis) {
        if (job == null || parentJob == null) return;
        while (job.isRunning()) {
            if (parentJob.stop_requested()) {
                job.stop();
            }
            try {
                Thread.sleep(pollingIntervalInMillis);
            } catch (InterruptedException ignored) {}
        }
    }

    final Key<EventLog> _eventLogKey;
    final Key<Leaderboard> _leaderboardKey;
    final Countdown _runCountdown;
    private int _pollingIntervalInMillis;

    private transient List<Job> _jobs; // subjobs
    private final AtomicInteger _modelCount = new AtomicInteger();

    ModelingStepsExecutor(Leaderboard leaderboard, EventLog eventLog, Countdown runCountdown) {
        _leaderboardKey = leaderboard._key;
        _eventLogKey = eventLog._key;
        _runCountdown = runCountdown;
    }
    
    void setPollingInterval(int millis) {
        assert millis > 0;
        _pollingIntervalInMillis = millis;
    }

    int modelCount() {
        return _modelCount.get();
    }

    void start() {
        start(DEFAULT_POLLING_INTERVAL_IN_MILLIS);
    }
    
    void start(int pollingIntervalInMillis) {
        setPollingInterval(pollingIntervalInMillis);
        _jobs = new ArrayList<>();
        _modelCount.set(0);
        _runCountdown.start();
    }

    void stop() {
        _runCountdown.stop();
        if (null == _jobs) return; // already stopped
        for (Job j : _jobs) j.stop();
        for (Job j : _jobs) j.get(); // Hold until they all completely stop.
        _jobs = null;
    }

    @SuppressWarnings("unchecked")
    boolean submit(ModelingStep step, Job parentJob) {
        boolean retVal = false;
        for (Iterator<ModelingStep> it = step.iterateSubSteps(); it.hasNext(); ) {
            retVal |= submit(it.next(), parentJob);
        }
        if (step.canRun()) {
            Job job = step.run();
            try {
                if (job==null) {
                    skip(step, parentJob);
                } else {
                    monitor(job, step, parentJob);
                    retVal = true;
                }
            } finally {
                step.onDone(job);
            }
        } else if (step.getAllocatedWork() != null) {
            step.getAllocatedWork().consume();
        }
        return retVal;
    }

    private void skip(ModelingStep step, Job parentJob) {
        if (null != parentJob) {
            String desc = step._description;
            Work work = step.getAllocatedWork();
            parentJob.update(work.consume(), "SKIPPED: "+desc);
            Log.info("AutoML; skipping "+desc);
        }
    }

    void monitor(Job job, ModelingStep step, Job parentJob) {
        EventLog eventLog = eventLog();
        String jobDescription = job._result == null ? job._description : job._result+" ["+job._description+"]";
        eventLog.debug(Stage.ModelTraining, jobDescription + " started");
        _jobs.add(job);

        boolean ignoreTimeout = ArrayUtils.contains(step._ignoredConstraints, Constraint.TIMEOUT);
        Work work = step.getAllocatedWork();
        long lastWorkedSoFar = 0;
        long lastTotalModelsBuilt = 0;

        while (job.isRunning()) {
            if (null != parentJob) {
                if (parentJob.stop_requested()) {
                    eventLog.debug(Stage.ModelTraining, "AutoML job cancelled; skipping " + jobDescription);
                    job.stop();
                }
                if (!ignoreTimeout && _runCountdown.timedOut()) {
                    eventLog.debug(Stage.ModelTraining, "AutoML: out of time; skipping " + jobDescription);
                    job.stop();
                }
            }
            long workedSoFar = Math.round(job.progress() * work._weight);

            if (null != parentJob) {
                parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), jobDescription);
            }

            if (JobType.HyperparamSearch == work._type || JobType.Selection == work._type) {
                ModelContainer<?> container = (ModelContainer)job._result.get();
                int totalModelsBuilt = container == null ? 0 : container.getModelCount();
                if (totalModelsBuilt > lastTotalModelsBuilt) {
                    eventLog.debug(Stage.ModelTraining, "Built: "+totalModelsBuilt+" models for "+work._type+" : "+jobDescription);
                    this.addModels(container, step);
                    lastTotalModelsBuilt = totalModelsBuilt;
                }
            }

            try {
                Thread.sleep(_pollingIntervalInMillis);
            }
            catch (InterruptedException e) {
                // keep going
            }
            lastWorkedSoFar = workedSoFar;
        }

        // pick up any stragglers:
        if (JobType.HyperparamSearch == work._type || JobType.Selection == work._type) {
            if (job.isCrashed()) {
                eventLog.warn(Stage.ModelTraining, jobDescription + " failed: " + job.ex().toString());
            } else if (job.get() == null) {
                eventLog.info(Stage.ModelTraining, jobDescription + " cancelled");
            } else {
                eventLog.debug(Stage.ModelTraining, jobDescription + " complete");
                ModelContainer<?> container = (ModelContainer) job.get();
                int totalModelsBuilt = container.getModelCount();
                if (totalModelsBuilt > lastTotalModelsBuilt) {
                    eventLog.debug(Stage.ModelTraining, "Built: "+totalModelsBuilt+" models for "+work._type+" : "+jobDescription);
                    this.addModels(container, step);
                }
            }
        } else if (JobType.ModelBuild == work._type) {
            if (job.isCrashed()) {
                eventLog.warn(Stage.ModelTraining, jobDescription + " failed: " + job.ex().toString());
            } else if (job.get() == null) {
                eventLog.info(Stage.ModelTraining, jobDescription + " cancelled");
            } else {
                eventLog.debug(Stage.ModelTraining, jobDescription + " complete");
                this.addModel((Model)job.get(), step);
            }
        }

        // add remaining work
        if (null != parentJob) {
            parentJob.update(work._weight - lastWorkedSoFar);
        }
        work.consume();
        _jobs.remove(job);
    }

    private void addModels(final ModelContainer container, ModelingStep step) {
        for (Key<Model> key : container.getModelKeys()) step.register(key);
        Leaderboard leaderboard = leaderboard();
        int before = leaderboard.getModelCount();
        leaderboard.addModels(container.getModelKeys());
        int after = leaderboard.getModelCount();
        _modelCount.addAndGet(after - before);
    }

    private void addModel(final Model model, ModelingStep step) {
        step.register(model._key);
        Leaderboard leaderboard = leaderboard();
        int before = leaderboard.getModelCount();
        leaderboard.addModel(model._key);
        int after = leaderboard.getModelCount();
        boolean ignoreModelCount = ArrayUtils.contains(step._ignoredConstraints, Constraint.MODEL_COUNT);
        if (!ignoreModelCount)
            _modelCount.addAndGet(after - before);
    }

    private EventLog eventLog() {
        return _eventLogKey.get();
    }

    private Leaderboard leaderboard() {
        return _leaderboardKey.get();
    }

}
