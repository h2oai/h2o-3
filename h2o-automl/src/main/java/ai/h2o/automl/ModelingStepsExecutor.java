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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Class responsible for starting all the {@link ModelingStep}s and monitoring their associated {@link Job},
 * i.e. polling jobs and adding their result model(s) to the {@link Leaderboard}.
 */
class ModelingStepsExecutor extends Iced<ModelingStepsExecutor> {

    private static final int pollingIntervalInMillis = 1000;

    final Key<EventLog> _eventLogKey;
    final Key<Leaderboard> _leaderboardKey;
    final Countdown _runCountdown;

    private transient List<Job> _jobs; // subjobs
    private final AtomicInteger _modelCount = new AtomicInteger();

    ModelingStepsExecutor(Leaderboard leaderboard, EventLog eventLog, Countdown runCountdown) {
        _leaderboardKey = leaderboard._key;
        _eventLogKey = eventLog._key;
        _runCountdown = runCountdown;
    }

    int modelCount() {
        return _modelCount.get();
    }

    void start() {
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

    boolean submit(ModelingStep step, Job parentJob) {
        if (step.canRun()) {
            Job job = step.startJob();
            if (job == null) {
                skip(step._description, step.getAllocatedWork(), parentJob);
            } else {
                monitor(job, step.getAllocatedWork(), parentJob, ArrayUtils.contains(step._ignoredConstraints, Constraint.TIMEOUT));
                return true;
            }
        }
        return false;
    }

    private void skip(String name, Work work, Job parentJob) {
        if (null != parentJob) {
            parentJob.update(work.consume(), "SKIPPED: " + name);
            Log.info("AutoML; skipping " + name);
        }
    }

    void monitor(Job job, Work work, Job parentJob, boolean ignoreTimeout) {
        EventLog eventLog = eventLog();
        String jobDescription = job._result == null ? job._description : job._result.toString()+" ["+job._description+"]";
        eventLog.debug(Stage.ModelTraining, jobDescription + " started");
        _jobs.add(job);

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
                    this.addModels(container);
                    lastTotalModelsBuilt = totalModelsBuilt;
                }
            }

            try {
                Thread.sleep(pollingIntervalInMillis);
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
                ModelContainer<?> container = (ModelContainer) job.get();
                int totalModelsBuilt = container.getModelCount();
                if (totalModelsBuilt > lastTotalModelsBuilt) {
                    eventLog.debug(Stage.ModelTraining, "Built: "+totalModelsBuilt+" models for "+work._type+" : "+jobDescription);
                    this.addModels(container);
                }
                eventLog.debug(Stage.ModelTraining, jobDescription + " complete");
            }
        } else if (JobType.ModelBuild == work._type) {
            if (job.isCrashed()) {
                eventLog.warn(Stage.ModelTraining, jobDescription + " failed: " + job.ex().toString());
            } else if (job.get() == null) {
                eventLog.info(Stage.ModelTraining, jobDescription + " cancelled");
            } else {
                eventLog.debug(Stage.ModelTraining, jobDescription + " complete");
                this.addModel((Model) job.get());
            }
        }

        // add remaining work
        if (null != parentJob) {
            parentJob.update(work._weight - lastWorkedSoFar);
        }
        work.consume();
        _jobs.remove(job);
    }

    private void addModels(final ModelContainer container) {
        Leaderboard leaderboard = leaderboard();
        int before = leaderboard.getModelCount();
        leaderboard.addModels(container.getModelKeys());
        int after = leaderboard.getModelCount();
        _modelCount.addAndGet(after - before);
    }

    private void addModel(final Model newModel) {
        Leaderboard leaderboard = leaderboard();
        int before = leaderboard.getModelCount();
        leaderboard.addModel(newModel._key);
        int after = leaderboard.getModelCount();
        _modelCount.addAndGet(after - before);
    }

    private EventLog eventLog() {
        return _eventLogKey.get();
    }

    private Leaderboard leaderboard() {
        return _leaderboardKey.get();
    }

}
