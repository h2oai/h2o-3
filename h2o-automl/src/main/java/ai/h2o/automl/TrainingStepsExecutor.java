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


/**
 * Class responsible for starting all the {@link TrainingStep}s and monitoring their associated {@link Job},
 * i.e. polling jobs and adding their result model(s) to the {@link Leaderboard}.
 */
class TrainingStepsExecutor extends Iced<TrainingStepsExecutor> {

    private static final int pollingIntervalInMillis = 1000;

    Key<EventLog> _eventLogKey;
    Key<Leaderboard> _leaderboardKey;
    Countdown _runCountdown;

    private transient List<Job> _jobs; // subjobs
    private AtomicInteger _modelCount = new AtomicInteger();

    TrainingStepsExecutor(Leaderboard leaderboard, EventLog eventLog, Countdown runCountdown) {
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

    boolean submit(TrainingStep step, Job parentJob) {
        if (step.canRun()) {
            Job job = step.startJob();
            if (job == null) {
                skip(step._description, step.getAllocatedWork(), parentJob);
            } else {
                monitor(job, step.getAllocatedWork(), parentJob, step._ignoreConstraints);
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

    private void monitor(Job job, Work work, Job parentJob, boolean ignoreTimeout) {
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
                Thread.sleep(pollingIntervalInMillis);
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
        return _eventLogKey.get();
    }

    private Leaderboard leaderboard() {
        return _leaderboardKey.get();
    }

}
