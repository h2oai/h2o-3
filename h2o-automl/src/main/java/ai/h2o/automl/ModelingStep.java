package ai.h2o.automl;

import ai.h2o.automl.AutoMLBuildSpec.AutoMLCustomParameters;
import ai.h2o.automl.ModelSelectionStrategies.LeaderboardHolder;
import ai.h2o.automl.ModelSelectionStrategy.Selection;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry;
import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.leaderboard.Leaderboard;
import ai.h2o.automl.preprocessing.PreprocessingStep;
import hex.Model;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.ModelBuilder;
import hex.ModelContainer;
import hex.ScoreKeeper.StoppingMetric;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import jsr166y.CountedCompleter;
import org.apache.commons.lang.builder.ToStringBuilder;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.Countdown;
import water.util.EnumUtils;
import water.util.Log;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Parent class defining common properties and common logic for actual {@link AutoML} training steps.
 */
public abstract class ModelingStep<M extends Model> extends Iced<ModelingStep> {

    protected enum SeedPolicy {
        /** No seed will be used (= random). */
        None,
        /** The global AutoML seed will be used. */
        Global,
        /** The seed is incremented for each model, starting from the global seed if there is one. */
        Incremental
    }

    static Predicate<Work> isDefaultModel = w -> w._type == JobType.ModelBuild;
    static Predicate<Work> isExplorationWork = w -> w._type == JobType.ModelBuild || w._type == JobType.HyperparamSearch;
    static Predicate<Work> isExploitationWork = w -> w._type == JobType.Selection;

    protected <MP extends Model.Parameters> Job<Grid> startSearch(
            final Key<Grid> resultKey,
            final MP baseParams,
            final Map<String, Object[]> hyperParams,
            final HyperSpaceSearchCriteria searchCriteria)
    {
        aml().eventLog().info(Stage.ModelTraining, "AutoML: starting "+resultKey+" hyperparameter search")
                .setNamedValue("start_"+_algo+"_"+_id, new Date(), EventLogEntry.epochFormat.get());
        return GridSearch.startGridSearch(
                resultKey,
                baseParams,
                hyperParams,
                new GridSearch.SimpleParametersBuilderFactory<>(),
                searchCriteria,
                GridSearch.SEQUENTIAL_MODEL_BUILDING
        );
    }

    protected <M extends Model, MP extends Model.Parameters> Job<M> startModel(
            final Key<M> resultKey,
            final MP params
    ) {
        Job<M> job = new Job<>(resultKey, ModelBuilder.javaName(_algo.urlName()), _description);
        ModelBuilder builder = ModelBuilder.make(_algo.urlName(), job, (Key<Model>) resultKey);
        builder._parms = params;
        aml().eventLog().info(Stage.ModelTraining, "AutoML: starting "+resultKey+" model training")
                .setNamedValue("start_"+_algo+"_"+_id, new Date(), EventLogEntry.epochFormat.get());
        builder.init(false);          // validate parameters
        try {
            return builder.trainModelOnH2ONode();
        } catch (H2OIllegalArgumentException exception) {
            aml().eventLog().warn(Stage.ModelTraining, "Skipping training of model "+resultKey+" due to exception: "+exception);
            onDone(null);
            return null;
        }
    }

    private transient AutoML _aml;

    protected final IAlgo _algo;
    protected final String _id;
    protected int _weight;
    protected AutoML.Constraint[] _ignoredConstraints = new AutoML.Constraint[0];  // whether or not to ignore the max_models/max_runtime constraints
    protected String _description;
    private final transient List<Consumer<Job>> _onDone = new ArrayList<>();

    StepDefinition _fromDef;

    protected ModelingStep(IAlgo algo, String id, int weight, AutoML autoML) {
        _algo = algo;
        _id = id;
        _weight = weight;
        _aml = autoML;
        _description = algo.name()+" "+id;
    }

    protected abstract Work getAllocatedWork();

    protected abstract Key makeKey(String name, boolean withCounter);

    protected abstract Work makeWork();

    protected abstract Job startJob();

    protected void onDone(Job job) {
        for (Consumer<Job> exec : _onDone) {
            exec.accept(job);
        }
        _onDone.clear();
    };

    protected AutoML aml() {
        return _aml;
    }

    protected boolean canRun() {
        Work work = getAllocatedWork();
        return work != null && work._weight > 0;
    }

    protected WorkAllocations getWorkAllocations() {
        return aml()._workAllocations;
    }

    protected Model[] getTrainedModels() {
        return aml().leaderboard().getModels();
    }

    protected Key<Model>[] getTrainedModelsKeys() {
        return aml().leaderboard().getModelKeys();
    }

    protected boolean isCVEnabled() {
        return aml().isCVEnabled();
    }

    /**
     * Assign common parameters to the model params before building the model or set of models.
     * This includes:
     * <ul>
     *   <li>data-related parameters: frame/columns parameters, class distribution.</li>
     *   <li>cross-validation parameters/</li>
     *   <li>memory-optimization: if certain objects build during training should be kept after training or not/</li>
     *   <li>model management: checkpoints.</li>
     * </ul>
     * @param params the model parameters to which the common parameters will be added.
     */
    protected void setCommonModelBuilderParams(Model.Parameters params) {
        params._train = aml()._trainingFrame._key;
        if (null != aml()._validationFrame)
            params._valid = aml()._validationFrame._key;

        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        params._response_column = buildSpec.input_spec.response_column;
        params._ignored_columns = buildSpec.input_spec.ignored_columns;

        setCrossValidationParams(params);
        setWeightingParams(params);
        setClassBalancingParams(params);
        applyPreprocessing(params);

        params._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
        params._keep_cross_validation_fold_assignment = buildSpec.build_control.nfolds != 0 && buildSpec.build_control.keep_cross_validation_fold_assignment;
        params._export_checkpoints_dir = buildSpec.build_control.export_checkpoints_dir;
    }
    
    protected void setCrossValidationParams(Model.Parameters params) {
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        params._keep_cross_validation_predictions = aml().getBlendingFrame() == null ? true : buildSpec.build_control.keep_cross_validation_predictions;
        params._fold_column = buildSpec.input_spec.fold_column;

        if (buildSpec.input_spec.fold_column == null) {
            params._nfolds = buildSpec.build_control.nfolds;
            if (buildSpec.build_control.nfolds > 1) {
                // TODO: below allow the user to specify this (vs Modulo)
                params._fold_assignment = FoldAssignmentScheme.Modulo;
            }
        }
    }
    
    protected void setWeightingParams(Model.Parameters params) {
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        params._weights_column = buildSpec.input_spec.weights_column;
    }
    
    protected void setClassBalancingParams(Model.Parameters params) {
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        if (buildSpec.build_control.balance_classes) {
            params._balance_classes = buildSpec.build_control.balance_classes;
            params._class_sampling_factors = buildSpec.build_control.class_sampling_factors;
            params._max_after_balance_size = buildSpec.build_control.max_after_balance_size;
        }
    }

    protected void setCustomParams(Model.Parameters params) {
        AutoMLCustomParameters customParams = aml().getBuildSpec().build_models.algo_parameters;
        if (customParams == null) return;
        customParams.applyCustomParameters(_algo, params);
    }
    
    protected void applyPreprocessing(Model.Parameters params) {
        if (aml().getPreprocessing() == null) return;
        for (PreprocessingStep preprocessingStep : aml().getPreprocessing()) {
            PreprocessingStep.Completer complete = preprocessingStep.apply(params);
            _onDone.add(j -> complete.run());
        }
    }
    

    /**
     * Configures early-stopping for the model or set of models to be built.
     *
     * @param parms the model parameters to which the stopping criteria will be added.
     * @param defaults the default parameters for the corresponding {@link ModelBuilder}.
     */
    protected void setStoppingCriteria(Model.Parameters parms, Model.Parameters defaults) {
        // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();

        //FIXME: Do we really need to compare with defaults before setting the buildSpec value instead?
        // This can create subtle bugs: e.g. if dev wanted to enforce a stopping criteria for a specific algo/model,
        // he wouldn't be able to enforce the default value, that would always be overridden by buildSpec.
        // We should instead provide hooks and ensure that properties are always set in the following order:
        //  1. defaults, 2. user defined, 3. internal logic/algo specific based on the previous state (esp. handling of AUTO properties).

        if (parms._stopping_metric == defaults._stopping_metric)
            parms._stopping_metric = buildSpec.build_control.stopping_criteria.stopping_metric();

        if (parms._stopping_metric == StoppingMetric.AUTO) {
            String sort_metric = getSortMetric();
            parms._stopping_metric = sort_metric == null ? StoppingMetric.AUTO
                    : sort_metric.equals("auc") ? StoppingMetric.logloss
                    : metricValueOf(sort_metric);
        }

        if (parms._stopping_rounds == defaults._stopping_rounds)
            parms._stopping_rounds = buildSpec.build_control.stopping_criteria.stopping_rounds();

        if (parms._stopping_tolerance == defaults._stopping_tolerance)
            parms._stopping_tolerance = buildSpec.build_control.stopping_criteria.stopping_tolerance();
    }

    /**
     * @param parms the model parameters to which the stopping criteria will be added.
     * @param defaults the default parameters for the corresponding {@link ModelBuilder}.
     * @param seedPolicy the policy defining how the seed will be assigned to the model parameters.
     */
    protected void setSeed(Model.Parameters parms, Model.Parameters defaults, SeedPolicy seedPolicy) {
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        // Don't use the same exact seed so that, e.g., if we build two GBMs they don't do the same row and column sampling.
        if (parms._seed == defaults._seed) {
            switch (seedPolicy) {
                case Global:
                    parms._seed = buildSpec.build_control.stopping_criteria.seed();
                    break;
                case Incremental:
                    parms._seed = _aml._incrementalSeed.get() == defaults._seed ? defaults._seed : _aml._incrementalSeed.getAndIncrement();
                    break;
                default:
                    break;
            }
        }
    }
    
    protected void initTimeConstraints(Model.Parameters parms, double upperLimit) {
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        if (parms._max_runtime_secs == 0) {
            double maxPerModel = buildSpec.build_control.stopping_criteria.max_runtime_secs_per_model();
            parms._max_runtime_secs = upperLimit <= 0 ? maxPerModel : Math.min(maxPerModel, upperLimit);
        }
    }

    private String getSortMetric() {
        //ensures that the sort metric is always updated according to the defaults set by leaderboard
        Leaderboard leaderboard = aml().leaderboard();
        return leaderboard == null ? null : leaderboard.getSortMetric();
    }

    private static StoppingMetric metricValueOf(String name) {
        if (name == null) return StoppingMetric.AUTO;
        switch (name) {
            case "mean_residual_deviance": return StoppingMetric.deviance;
            default:
                try {
                    return EnumUtils.valueOf(StoppingMetric.class, name);
                } catch (IllegalArgumentException ignored) { }
                return StoppingMetric.AUTO;
        }
    }

    /**
     * Convenient base class for single/default model steps.
     */
    public static abstract class ModelStep<M extends Model> extends ModelingStep<M> {

        public static final int DEFAULT_MODEL_TRAINING_WEIGHT = 10;

        public ModelStep(IAlgo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected abstract Job<M> startJob();

        @Override
        protected Work makeWork() {
            return new Work(_id, _algo, JobType.ModelBuild, _weight);
        }

        @Override
        protected Work getAllocatedWork() {
            return getWorkAllocations().getAllocation(_id, _algo);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Key<M> makeKey(String name, boolean withCounter) {
            return aml().makeKey(name, null,  withCounter);
        }

        protected Job<M> trainModel(Model.Parameters parms) {
            return trainModel(null, parms);
        }

        /**
         * @param key (optional) model key.
         * @param parms the model builder params.
         * @return a started training model.
         */
        protected Job<M> trainModel(Key<M> key, Model.Parameters parms) {
            String algoName = ModelBuilder.algoName(_algo.urlName());

            if (null == key) key = makeKey(algoName, true);

            Model.Parameters defaults = ModelBuilder.make(_algo.urlName(), null, null)._parms;
            initTimeConstraints(parms, 0);
            setCommonModelBuilderParams(parms);
            setSeed(parms, defaults, SeedPolicy.Incremental);
            setStoppingCriteria(parms, defaults);
            setCustomParams(parms);

            // override model's max_runtime_secs to ensure that the total max_runtime doesn't exceed expectations
            if (ArrayUtils.contains(_ignoredConstraints, AutoML.Constraint.TIMEOUT)) {
                parms._max_runtime_secs = 0;
            } else {
                Work work = getAllocatedWork();
//                double maxAssignedTimeSecs = aml().timeRemainingMs() / 1e3; // legacy
                double maxAssignedTimeSecs = aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3; //including default models in the distribution of the time budget.
//                double maxAssignedTimeSecs = aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work, isDefaultModel) / 1e3; //PUBDEV-7595
                parms._max_runtime_secs = parms._max_runtime_secs == 0
                        ? maxAssignedTimeSecs
                        : Math.min(parms._max_runtime_secs, maxAssignedTimeSecs);
            }
            Log.debug("Training model: " + algoName + ", time remaining (ms): " + aml().timeRemainingMs());
            aml().eventLog().debug(Stage.ModelTraining, parms._max_runtime_secs == 0
                    ? "No time limitation for "+key
                    : "Time assigned for "+key+": "+parms._max_runtime_secs+"s");
            return startModel(key, parms);
        }
    }

    /**
     * Convenient base class for steps defining a (random) grid search.
     */
    public static abstract class GridStep<M extends Model> extends ModelingStep<M> {

        public static final int DEFAULT_GRID_TRAINING_WEIGHT = 20;

        public GridStep(IAlgo algo, String id, int cost, AutoML autoML) {
            super(algo, id, cost, autoML);
        }

        @Override
        protected abstract Job<Grid> startJob();

        @Override
        protected Work makeWork() {
            return new Work(_id, _algo, JobType.HyperparamSearch, _weight);
        }

        @Override
        protected Work getAllocatedWork() {
            return getWorkAllocations().getAllocation(_id, _algo);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Key<Grid> makeKey(String name, boolean withCounter) {
            return aml().makeKey(name, "grid", withCounter);
        }

        protected Job<Grid> hyperparameterSearch(Model.Parameters baseParms, Map<String, Object[]> searchParms) {
            return hyperparameterSearch(null, baseParms, searchParms);
        }

        /**
         * @param key optional grid key
         * @param baseParms ModelBuilder parameter values that are common across all models in the search.
         * @param searchParms hyperparameter search space,
         * @return the started hyperparameter search job.
         */
        protected Job<Grid> hyperparameterSearch(Key<Grid> key, Model.Parameters baseParms, Map<String, Object[]> searchParms) {
            Model.Parameters defaults;
            try {
                defaults = baseParms.getClass().newInstance();
            } catch (Exception e) {
                aml().eventLog().warn(Stage.ModelTraining, "Internal error doing hyperparameter search");
                throw new H2OIllegalArgumentException("Hyperparameter search can't create a new instance of Model.Parameters subclass: " + baseParms.getClass());
            }

            initTimeConstraints(baseParms, 0);
            setCommonModelBuilderParams(baseParms);
            // grid seed is provided later through the searchCriteria
            setStoppingCriteria(baseParms, defaults);
            setCustomParams(baseParms);

            AutoMLBuildSpec buildSpec = aml().getBuildSpec();
            RandomDiscreteValueSearchCriteria searchCriteria = (RandomDiscreteValueSearchCriteria)buildSpec.build_control.stopping_criteria.getSearchCriteria().clone();

            Work work = getAllocatedWork();
            // for time limit, this is allocated in proportion of the entire work budget.
            double maxAssignedTimeSecs = ArrayUtils.contains(_ignoredConstraints, AutoML.Constraint.TIMEOUT)
                    ? 0
                    : aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3;
            // SE predicate can be removed if/when we decide to include SEs in the max_models limit
            // for models limit, this is not assigned in the same proportion as for time,
            // as the exploitation phase is not supposed to "add" models but just to replace some by better ones,
            // instead, allocation is done in proportion of the entire exploration budget.
            int maxAssignedModels = (int) Math.ceil(aml().remainingModels() * getWorkAllocations().remainingWorkRatio(work, isExplorationWork.and(w -> w._algo != Algo.StackedEnsemble)));

            searchCriteria.set_max_runtime_secs(searchCriteria.max_runtime_secs() == 0
                    ? maxAssignedTimeSecs
                    : Math.min(searchCriteria.max_runtime_secs(), maxAssignedTimeSecs));

            searchCriteria.set_max_models(searchCriteria.max_models() == 0
                    ? maxAssignedModels
                    : Math.min(searchCriteria.max_models(), maxAssignedModels));

            if (null == key) key = makeKey(_algo.name(), true);
            aml().trackKey(key);

            Log.debug("Hyperparameter search: "+_algo.name()+", time remaining (ms): "+aml().timeRemainingMs());
            aml().eventLog().debug(Stage.ModelTraining, searchCriteria.max_runtime_secs() == 0
                    ? "No time limitation for "+key
                    : "Time assigned for "+key+": "+searchCriteria.max_runtime_secs()+"s");
            return startSearch(
                    key,
                    baseParms,
                    searchParms,
                    searchCriteria
            );
        }
    }

    public static abstract class SelectionStep<M extends Model> extends ModelingStep<M> {

        public SelectionStep(IAlgo algo, String id, int weight, AutoML autoML) {
            super(algo, id, weight, autoML);
            _ignoredConstraints = new AutoML.Constraint[] { AutoML.Constraint.MODEL_COUNT };
        }

        @Override
        protected Work makeWork() {
            return new Work(_id, _algo, JobType.Selection, _weight);
        }

        @Override
        protected Work getAllocatedWork() {
            return getWorkAllocations().getAllocation(_id, _algo);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Key<Models> makeKey(String name, boolean withCounter) {
            return aml().makeKey(name, "selection", withCounter);
        }

        private LeaderboardHolder makeLeaderboard(String name, EventLog eventLog) {
            Leaderboard amlLeaderboard = aml().leaderboard();
            EventLog tmpEventLog = eventLog == null ? EventLog.getOrMake(Key.make(name)) : eventLog;
            Leaderboard tmpLeaderboard = Leaderboard.getOrMake(
                    name,
                    tmpEventLog,
                    amlLeaderboard.leaderboardFrame(),
                    amlLeaderboard.getSortMetric()
            );
            return new LeaderboardHolder() {
                @Override
                public Leaderboard get() {
                    return tmpLeaderboard;
                }

                @Override
                public void cleanup() {
                    //by default, just empty the leaderboard and remove the container without touching anything model-related.
                    tmpLeaderboard.removeModels(tmpLeaderboard.getModelKeys(), false);
                    tmpLeaderboard.remove(false);
                    if (eventLog == null) {
                        tmpEventLog.remove();
                    }
                }
            };
        }

        protected LeaderboardHolder makeTmpLeaderboard(String name) {
            return makeLeaderboard("tmp_"+name, null);
        }

        @Override
        protected Job<Models> startJob() {
            Key<Model>[] trainedModelKeys = getTrainedModelsKeys();
            Key<Models> key = makeKey(_algo+"_"+_id, false);
            aml().trackKey(key);
            Job<Models> job = new Job<>(key, Models.class.getName(), _description);
            Work work = getAllocatedWork();

            double maxAssignedTimeSecs = ArrayUtils.contains(_ignoredConstraints, AutoML.Constraint.TIMEOUT)
                    ? 0
                    : aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3;

            aml().eventLog().debug(Stage.ModelTraining, maxAssignedTimeSecs == 0
                    ? "No time limitation for "+key
                    : "Time assigned for "+key+": "+maxAssignedTimeSecs+"s");

            return job.start(new H2O.H2OCountedCompleter() {

                Models result = new Models(key, Model.class, job);
                Key<Models> selectionKey = Key.make(key+"_select");
                EventLog selectionEventLog = EventLog.getOrMake(selectionKey);
//                EventLog selectionEventLog = aml().eventLog();
                LeaderboardHolder selectionLeaderboard = makeLeaderboard(selectionKey.toString(), selectionEventLog);

                {
                    result.delete_and_lock(job);
                }

                @Override
                public void compute2() {
                    Countdown countdown = Countdown.fromSeconds(maxAssignedTimeSecs);
                    ModelingStepsExecutor localExecutor = new ModelingStepsExecutor(selectionLeaderboard.get(), selectionEventLog, countdown);
                    localExecutor.start();
                    Job<Models> innerTraining = startTraining(selectionKey, maxAssignedTimeSecs);
                    localExecutor.monitor(innerTraining, work, job, false);

                    Log.debug("Selection leaderboard " + selectionLeaderboard.get()._key, selectionLeaderboard.get().toLogString());
                    Selection selection = getSelectionStrategy().select(trainedModelKeys, selectionLeaderboard.get().getModelKeys());
                    Leaderboard lb = aml().leaderboard();
                    Log.debug("Selection result for job " + key, ToStringBuilder.reflectionToString(selection));
                    lb.removeModels(selection._remove, true);
                    lb.addModels(selection._add);

                    result.unlock(job);
                    result.addModels(selection._add);
                    tryComplete();
                }

                @Override
                public void onCompletion(CountedCompleter caller) {
                    Keyed.remove(selectionKey, new Futures(), false); // don't cascade: tmp models removal is is done using the logic below.
                    selectionLeaderboard.get().removeModels(trainedModelKeys, false); // if original models were added to selection leaderboard, just remove them.
                    selectionLeaderboard.get().removeModels( // for newly trained models, fully remove those that don't appear in the result container.
                            Arrays.stream(selectionLeaderboard.get().getModelKeys()).filter(k -> !ArrayUtils.contains(result.getModelKeys(), k)).toArray(Key[]::new),
                            true
                    );
                    selectionLeaderboard.cleanup();
                    if (!aml().eventLog()._key.equals(selectionEventLog._key)) selectionEventLog.remove();
                    super.onCompletion(caller);
                }

                @Override
                public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
                    result.unlock(job._key, false);
                    Keyed.remove(selectionKey);
                    selectionLeaderboard.get().remove();
                    if (!aml().eventLog()._key.equals(selectionEventLog._key)) selectionEventLog.remove();
                    return super.onExceptionalCompletion(ex, caller);
                }

            }, work._weight, maxAssignedTimeSecs);
        }

        protected abstract Job<Models> startTraining(Key<Models> result, double maxRuntimeSecs);

        protected abstract ModelSelectionStrategy getSelectionStrategy();

        protected Job<Models> asModelsJob(Job job, Key<Models> result){
            Job<Models> jModels = new Job<>(result, Models.class.getName(), job._description); // can use the same result key as original job, as it is dropped once its result is read
            return jModels.start(new H2O.H2OCountedCompleter() {

                Models models = new Models(result, Model.class, jModels);
                {
                    models.delete_and_lock(jModels);
                }

                @Override
                public void compute2() {
                    ModelingStepsExecutor.ensureStopRequestPropagated(job, jModels);
                    Keyed res = job.get();
                    models.unlock(jModels);
                    if (res instanceof Model) {
                        models.addModel(((Model)res)._key);
                    } else if (res instanceof ModelContainer) {
                        models.addModels(((ModelContainer)res).getModelKeys());
                        res.remove(false);
                    } else {
                        throw new H2OIllegalArgumentException("Can only convert jobs producing a single Model or ModelContainer.");
                    }
                    tryComplete();
                }
            }, job._work, job._max_runtime_msecs);
        }
    }

}
