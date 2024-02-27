package ai.h2o.automl;

import ai.h2o.automl.AutoML.Constraint;
import ai.h2o.automl.StepResultState.ResultStatus;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry.Stage;
import hex.Model;
import hex.ModelContainer;
import hex.leaderboard.Leaderboard;
import water.Iced;
import water.Job;
import water.Key;
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
    static final StepResultState.Resolution DEFAULT_STATE_RESOLUTION_STRATEGY = StepResultState.Resolution.optimistic;
    
    static void ensureStopRequestPropagated(Job job, Job parentJob, int pollingIntervalInMillis) {
        if (job == null || parentJob == null) return;
        while (job.isRunning()) {
            if (parentJob.stop_requested()) {
                job.stop();
            }
            job.blockingWaitForDone(pollingIntervalInMillis);
        }
    }

    final Key<EventLog> _eventLogKey;
    final Key<Leaderboard> _leaderboardKey;
    final Countdown _runCountdown;
    private int _pollingIntervalInMillis;
    private StepResultState.Resolution _stateResolutionStrategy;

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
    
    void setStateResolutionStrategy(StepResultState.Resolution strategy) {
        assert strategy != null;
        _stateResolutionStrategy = strategy;
    }

    int modelCount() {
        return _modelCount.get();
    }

    void start() {
        start(DEFAULT_POLLING_INTERVAL_IN_MILLIS, DEFAULT_STATE_RESOLUTION_STRATEGY);
    }
    
    void start(int pollingIntervalInMillis, StepResultState.Resolution strategy) {
        setPollingInterval(pollingIntervalInMillis);
        setStateResolutionStrategy(strategy);
        _jobs = new ArrayList<>();
        _modelCount.set(0);
        _runCountdown.start();
    }

    void stop() {
        _runCountdown.stop();
        if (_jobs == null) return; // already stopped
        for (Job j : _jobs) j.stop();
        for (Job j : _jobs) j.get(); // Hold until they all completely stop.
        _jobs = null;
    }

    @SuppressWarnings("unchecked")
    StepResultState submit(ModelingStep step, Job parentJob) {
        StepResultState resultState = new StepResultState(step.getGlobalId());
        for (Iterator<ModelingStep> it = step.iterateSubSteps(); it.hasNext(); ) {
            resultState.addState(submit(it.next(), parentJob));
        }
        if (step.canRun()) {
            Job job = null;
            try {
                job = step.run();
                if (job == null) {
                    resultState.addState(skip(step, parentJob));
                } else {
                    resultState.addState(monitor(job, step, parentJob));
                }
            } catch (Exception e) {
                resultState.addState(new StepResultState(step.getGlobalId(), e));
            } finally {
                step.onDone(job);
            }
        } else {
            resultState.addState(new StepResultState(step.getGlobalId(), ResultStatus.skipped));
            if (step.getAllocatedWork() != null) {
                step.getAllocatedWork().consume();
            }
        }
        resultState.resolveState(_stateResolutionStrategy);
        return resultState;
    }

    private StepResultState skip(ModelingStep step, Job parentJob) {
        if (null != parentJob) {
            String desc = step._description;
            Work work = step.getAllocatedWork();
            parentJob.update(work.consume(), "SKIPPED: "+desc);
            Log.info("AutoML; skipping "+desc);
        }
        return new StepResultState(step.getGlobalId(), ResultStatus.skipped);
    }

    StepResultState monitor(Job job, ModelingStep step, Job parentJob) {
        EventLog eventLog = eventLog();
        String jobDescription = job._result == null ? job._description : job._result+" ["+job._description+"]";
        eventLog.debug(Stage.ModelTraining, jobDescription + " started");
        _jobs.add(job);

        boolean ignoreTimeout = step.ignores(Constraint.TIMEOUT);
        Work work = step.getAllocatedWork();
        long lastWorkedSoFar = 0;
        long lastTotalModelsBuilt = 0;

        try {
            while (job.isRunning()) {
                if (parentJob != null) {
                    if (parentJob.stop_requested()) {
                        eventLog.debug(Stage.ModelTraining, "AutoML job cancelled; skipping "+jobDescription);
                        job.stop();
                    }
                    if (!ignoreTimeout && _runCountdown.timedOut()) {
                        eventLog.debug(Stage.ModelTraining, "AutoML: out of time; skipping "+jobDescription);
                        job.stop();
                    }
                }
                long workedSoFar = Math.round(job.progress() * work._weight);

                if (parentJob != null) {
                    parentJob.update(Math.round(workedSoFar - lastWorkedSoFar), jobDescription);
                }

                if (JobType.HyperparamSearch == work._type || JobType.Selection == work._type) {
                    ModelContainer<?> container = (ModelContainer) job._result.get();
                    int totalModelsBuilt = container == null ? 0 : container.getModelCount();
                    if (totalModelsBuilt > lastTotalModelsBuilt) {
                        eventLog.debug(Stage.ModelTraining, "Built: "+totalModelsBuilt+" models for "+work._type+" : "+jobDescription);
                        this.addModels(container, step);
                        lastTotalModelsBuilt = totalModelsBuilt;
                    }
                }

                job.blockingWaitForDone(_pollingIntervalInMillis);
                lastWorkedSoFar = workedSoFar;
            }

            if (job.isCrashed()) {
                eventLog.error(Stage.ModelTraining, jobDescription+" failed: "+job.ex());
                return new StepResultState(step.getGlobalId(), job.ex());
            } else if (job.get() == null) {
                eventLog.info(Stage.ModelTraining, jobDescription+" cancelled");
                return new StepResultState(step.getGlobalId(), ResultStatus.cancelled);
            } else {
                // pick up any stragglers:
                if (JobType.HyperparamSearch == work._type || JobType.Selection == work._type) {
                    eventLog.debug(Stage.ModelTraining, jobDescription+" complete");
                    ModelContainer<?> container = (ModelContainer) job.get();
                    int totalModelsBuilt = container.getModelCount();
                    if (totalModelsBuilt > lastTotalModelsBuilt) {
                        eventLog.debug(Stage.ModelTraining, "Built: "+totalModelsBuilt+" models for "+work._type+" : "+jobDescription);
                        this.addModels(container, step);
                    }
                } else if (JobType.ModelBuild == work._type) {
                    eventLog.debug(Stage.ModelTraining, jobDescription+" complete");
                    this.addModel((Model) job.get(), step);
                }
                return new StepResultState(step.getGlobalId(), ResultStatus.success);
            }
        } finally {
            // add remaining work
            if (parentJob != null) {
                parentJob.update(work._weight - lastWorkedSoFar);
            }
            work.consume();
            _jobs.remove(job);
        }
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
        if (!step.ignores(Constraint.MODEL_COUNT))
            _modelCount.addAndGet(after - before);
    }

    private EventLog eventLog() {
        return _eventLogKey.get();
    }

    private Leaderboard leaderboard() {
        return Leaderboard.getInstance(_leaderboardKey, eventLog().asLogger(Stage.ModelTraining));
    }

}
