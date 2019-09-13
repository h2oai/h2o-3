package ai.h2o.automl;

import ai.h2o.automl.EventLogEntry.Stage;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import hex.Model;
import hex.grid.Grid;
import water.Iced;
import water.Job;
import water.Key;
import water.util.Countdown;
import water.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


class TrainingStepsExecutor extends Iced<TrainingStepsExecutor> {

    private transient List<Job> _jobs; // subjobs

    private EventLog _eventLog;
    private Leaderboard _leaderboard;
    private AtomicInteger _modelCount = new AtomicInteger();
    private Countdown _runCountdown;

    TrainingStepsExecutor(Leaderboard leaderboard, EventLog eventLog, Countdown runCountdown) {
        _jobs = new ArrayList<>();
        _leaderboard = leaderboard;
        _eventLog = eventLog;
        _runCountdown = runCountdown;
    }

    int modelCount() {
        return _modelCount.get();
    }

    void start() {
        _modelCount.set(0);
    }

    void stop() {
        if (null == _jobs) return; // already stopped
        for (Job j : _jobs) j.stop();
        for (Job j : _jobs) j.get(); // Hold until they all completely stop.
        _jobs = null;
    }

    //TODO
    void skip(String name, Work work, Job parentJob) {
        if (null != parentJob) {
            parentJob.update(work.consume(), "SKIPPED: " + name);
            Log.info("AutoML; skipping " + name);
        }
    }

    void submit(TrainingStep step, Job parentJob) {
        if (step.canRun()) {
            Job job = step.makeJob();
            if (job == null) {
                skip(step._description, step.getWork(), parentJob);
            } else {
                submit(job, step.getWork(), parentJob, step._ignoreConstraints);
            }
        }
    }

    void submit(Job job, Work work, Job parentJob, boolean ignoreTimeout) {
        String jobDescription = job._result == null ? job._description : job._result.toString()+" ["+job._description+"]";
        eventLog().debug(Stage.ModelTraining, jobDescription + " started");
        _jobs.add(job);

        long lastWorkedSoFar = 0;
        long lastTotalGridModelsBuilt = 0;

        while (job.isRunning()) {
            if (null != parentJob) {
                if (parentJob.stop_requested()) {
                    eventLog().debug(Stage.ModelTraining, "AutoML job cancelled; skipping " + jobDescription);
                    job.stop();
                }
                if (!ignoreTimeout && _runCountdown.timedOut()) {
                    eventLog().debug(Stage.ModelTraining, "AutoML: out of time; skipping " + jobDescription);
                    job.stop();
                }
            }
            long workedSoFar = Math.round(job.progress() * work._weight);

            if (null != parentJob) {
                parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), jobDescription);
            }

            if (JobType.HyperparamSearch == work._type) {
                Grid<?> grid = (Grid)job._result.get();
                int totalGridModelsBuilt = grid.getModelCount();
                if (totalGridModelsBuilt > lastTotalGridModelsBuilt) {
                    eventLog().debug(Stage.ModelTraining, "Built: " + totalGridModelsBuilt + " models for search: " + jobDescription);
                    this.addModels(grid.getModelKeys());
                    lastTotalGridModelsBuilt = totalGridModelsBuilt;
                }
            }

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // keep going
            }
            lastWorkedSoFar = workedSoFar;
        }

        // pick up any stragglers:
        if (JobType.HyperparamSearch == work._type) {
            if (job.isCrashed()) {
                eventLog().warn(Stage.ModelTraining, jobDescription + " failed: " + job.ex().toString());
            } else if (job.get() == null) {
                eventLog().info(Stage.ModelTraining, jobDescription + " cancelled");
            } else {
                Grid<?> grid = (Grid) job.get();
                int totalGridModelsBuilt = grid.getModelCount();
                if (totalGridModelsBuilt > lastTotalGridModelsBuilt) {
                    eventLog().debug(Stage.ModelTraining, "Built: " + totalGridModelsBuilt + " models for search: " + jobDescription);
                    this.addModels(grid.getModelKeys());
                }
                eventLog().debug(Stage.ModelTraining, jobDescription + " complete");
            }
        } else if (JobType.ModelBuild == work._type) {
            if (job.isCrashed()) {
                eventLog().warn(Stage.ModelTraining, jobDescription + " failed: " + job.ex().toString());
            } else if (job.get() == null) {
                eventLog().info(Stage.ModelTraining, jobDescription + " cancelled");
            } else {
                eventLog().debug(Stage.ModelTraining, jobDescription + " complete");
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


    // If we have multiple AutoML engines running on the same project they will be updating the Leaderboard concurrently,
    // so always use leaderboard() instead of the raw field, to get it from the DKV.
    // Also, the leaderboard will reject duplicate models, so use the difference in Leaderboard length here.
    private void addModels(final Key<Model>[] newModels) {
        int before = leaderboard().getModelCount();
        leaderboard().addModels(newModels);
        int after = leaderboard().getModelCount();
        _modelCount.addAndGet(after - before);
    }

    private void addModel(final Model newModel) {
        int before = leaderboard().getModelCount();
        leaderboard().addModel(newModel);
        int after = leaderboard().getModelCount();
        _modelCount.addAndGet(after - before);
    }

    private EventLog eventLog() {
        return _eventLog == null ? null : (_eventLog = _eventLog._key.get());
    }

    private Leaderboard leaderboard() {
        return _leaderboard == null ? null : (_leaderboard = _leaderboard._key.get());
    }

}
