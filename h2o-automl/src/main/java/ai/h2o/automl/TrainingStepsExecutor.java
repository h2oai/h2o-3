package ai.h2o.automl;

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

    void submit(EventLogEntry.Stage stage, String name, WorkAllocations.Work work, Job parentJob, Job subJob) {
        submit(stage, name, work, parentJob, subJob, false);
    }

    void submit(EventLogEntry.Stage stage, String name, WorkAllocations.Work work, Job parentJob, Job subJob, boolean ignoreTimeout) {
        if (null == subJob) {
            if (null != parentJob) {
                parentJob.update(work.consume(), "SKIPPED: " + name);
                Log.info("AutoML skipping " + name);
            }
            return;
        }
        eventLog().debug(stage, name + " started");
        _jobs.add(subJob);

        long lastWorkedSoFar = 0;
        long lastTotalGridModelsBuilt = 0;

        while (subJob.isRunning()) {
            if (null != parentJob) {
                if (parentJob.stop_requested()) {
                    eventLog().debug(stage, "AutoML job cancelled; skipping " + name);
                    subJob.stop();
                }
                if (!ignoreTimeout && _runCountdown.timedOut()) {
                    eventLog().debug(stage, "AutoML: out of time; skipping " + name);
                    subJob.stop();
                }
            }
            long workedSoFar = Math.round(subJob.progress() * work.share);

            if (null != parentJob) {
                parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), name);
            }

            if (AutoML.JobType.HyperparamSearch == work.type) {
                Grid<?> grid = (Grid)subJob._result.get();
                int totalGridModelsBuilt = grid.getModelCount();
                if (totalGridModelsBuilt > lastTotalGridModelsBuilt) {
                    eventLog().debug(stage, "Built: " + totalGridModelsBuilt + " models for search: " + name);
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
        if (AutoML.JobType.HyperparamSearch == work.type) {
            if (subJob.isCrashed()) {
                eventLog().warn(stage, name + " failed: " + subJob.ex().toString());
            } else if (subJob.get() == null) {
                eventLog().info(stage, name + " cancelled");
            } else {
                Grid<?> grid = (Grid) subJob.get();
                int totalGridModelsBuilt = grid.getModelCount();
                if (totalGridModelsBuilt > lastTotalGridModelsBuilt) {
                    eventLog().debug(stage, "Built: " + totalGridModelsBuilt + " models for search: " + name);
                    this.addModels(grid.getModelKeys());
                }
                eventLog().debug(stage, name + " complete");
            }
        } else if (AutoML.JobType.ModelBuild == work.type) {
            if (subJob.isCrashed()) {
                eventLog().warn(stage, name + " failed: " + subJob.ex().toString());
            } else if (subJob.get() == null) {
                eventLog().info(stage, name + " cancelled");
            } else {
                eventLog().debug(stage, name + " complete");
                this.addModel((Model) subJob.get());
            }
        }

        // add remaining work
        if (null != parentJob) {
            parentJob.update(work.share - lastWorkedSoFar);
        }
        work.consume();
        _jobs.remove(subJob);
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
