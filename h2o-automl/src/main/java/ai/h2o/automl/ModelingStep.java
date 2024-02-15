package ai.h2o.automl;

import ai.h2o.automl.AutoMLBuildSpec.AutoMLCustomParameters;
import ai.h2o.automl.ModelSelectionStrategies.LeaderboardHolder;
import ai.h2o.automl.ModelSelectionStrategy.Selection;
import ai.h2o.automl.StepResultState.ResultStatus;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry;
import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.automl.preprocessing.PreprocessingConfig;
import ai.h2o.automl.preprocessing.PreprocessingStep;
import hex.Model;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.ModelBuilder;
import hex.ModelContainer;
import hex.ScoreKeeper.StoppingMetric;
import hex.genmodel.utils.DistributionFamily;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.leaderboard.Leaderboard;
import hex.ModelParametersDelegateBuilderFactory;
import hex.pipeline.PipelineModel.PipelineParameters;
import jsr166y.CountedCompleter;
import org.apache.commons.lang.builder.ToStringBuilder;
import water.*;
import water.KeyGen.ConstantKeyGen;
import water.KeyGen.PatternKeyGen;
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
  
  protected static final String PIPELINE_KEY_PREFIX = "Pipeline_";

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
        assert resultKey != null;
        assert baseParams != null;
        assert hyperParams.size() > 0;
        assert searchCriteria != null;
        GridSearch.Builder builder = makeGridBuilder(resultKey, baseParams, hyperParams, searchCriteria);
        aml().trackKeys(builder.dest());
        aml().eventLog().info(Stage.ModelTraining, "AutoML: starting "+builder.dest()+" hyperparameter search")
                .setNamedValue("start_"+_provider+"_"+_id, new Date(), EventLogEntry.epochFormat.get());
        return builder.start();
    }

    @SuppressWarnings("unchecked")
    protected <MP extends Model.Parameters> Job<M> startModel(
            final Key<M> resultKey,
            final MP params
    ) {
        assert resultKey != null;
        assert params != null;
        ModelBuilder builder = makeBuilder(resultKey, params);
        aml().eventLog().info(Stage.ModelTraining, "AutoML: starting "+builder.dest()+" model training")
                .setNamedValue("start_"+_provider+"_"+_id, new Date(), EventLogEntry.epochFormat.get());
        builder.init(false);          // validate parameters
        if (builder._messages.length > 0) {
            for (ModelBuilder.ValidationMessage vm : builder._messages) {
                if (vm.log_level() == Log.WARN) {
                    aml().eventLog().warn(Stage.ModelTraining, vm.field()+" param, "+vm.message());
                } else if (vm.log_level() == Log.ERRR) {
                    aml().eventLog().error(Stage.ModelTraining, vm.field()+" param, "+vm.message());
                }
            }
        }
        return builder.trainModelOnH2ONode();
    }
    
    protected <MP extends Model.Parameters> GridSearch.Builder makeGridBuilder(Key<Grid> resultKey,
                                                                               MP baseParams,
                                                                               Map<String, Object[]> hyperParams,
                                                                               HyperSpaceSearchCriteria searchCriteria) {
      applyPreprocessing(baseParams);
      Model.Parameters finalParams = applyPipeline(resultKey, baseParams, hyperParams);
      if (finalParams instanceof PipelineParameters) resultKey = Key.make(PIPELINE_KEY_PREFIX+resultKey);
      return GridSearch.create(
                      resultKey,
                      HyperSpaceWalker.BaseWalker.WalkerFactory.create(
                              finalParams,
                              hyperParams,
                              new ModelParametersDelegateBuilderFactory<>(),
                              searchCriteria
                      ))
              .withParallelism(GridSearch.SEQUENTIAL_MODEL_BUILDING)
              .withMaxConsecutiveFailures(aml()._maxConsecutiveModelFailures);
    }
    
    
    protected <MP extends Model.Parameters> ModelBuilder makeBuilder(Key<M> resultKey, MP params) {
      applyPreprocessing(params);
      Model.Parameters finalParams = applyPipeline(resultKey, params, null);
      if (finalParams instanceof PipelineParameters) resultKey = Key.make(PIPELINE_KEY_PREFIX+resultKey);
      
      Job<M> job = new Job<>(resultKey, ModelBuilder.javaName(_algo.urlName()), _description);
      ModelBuilder builder = ModelBuilder.make(finalParams.algoName(), job, (Key<Model>) resultKey);
      builder._parms = finalParams;
      builder._input_parms = finalParams.clone();
      return builder;
    }

    private boolean validParameters(Model.Parameters parms, String[] fields) {
        try {
            Model.Parameters params = parms.clone();
            // some algos check if distribution has proper _nclass(es) so we need to set training frame and response etc
            setCommonModelBuilderParams(params);
            ModelBuilder mb = ModelBuilder.make(params);
            mb.init(false);
            return Arrays.stream(fields)
                    .allMatch((field) ->
                            mb.getMessagesByFieldAndSeverity(field, Log.ERRR).length == 0);
        } catch (H2OIllegalArgumentException e) {
            return false;
        }
    }

    protected void setDistributionParameters(Model.Parameters parms) {
        switch (aml().getDistributionFamily()) {
            case custom:
                parms._custom_distribution_func = aml().getBuildSpec().build_control.custom_distribution_func;
                break;
            case huber:
                parms._huber_alpha = aml().getBuildSpec().build_control.huber_alpha;
                break;
            case tweedie:
                parms._tweedie_power = aml().getBuildSpec().build_control.tweedie_power;
                break;
            case quantile:
                parms._quantile_alpha = aml().getBuildSpec().build_control.quantile_alpha;
                break;
        }

        try {
            parms.setDistributionFamily(aml().getDistributionFamily());
        } catch (H2OIllegalArgumentException e) {
            parms.setDistributionFamily(DistributionFamily.AUTO);
        }
        if (!validParameters(parms, new String[]{"_distribution", "_family"}))
            parms.setDistributionFamily(DistributionFamily.AUTO);

        if (!aml().getDistributionFamily().equals(parms.getDistributionFamily())) {
            aml().eventLog().info(Stage.ModelTraining,"Algo " + parms.algoName() +
                    " doesn't support " + _aml.getDistributionFamily().name() + " distribution. Using AUTO distribution instead.");
        }
    }

    private final transient AutoML _aml;

    protected final IAlgo _algo;
    protected final String _provider;
    protected final String _id;
    protected int _weight;
    protected int _priorityGroup;
    protected AutoML.Constraint[] _ignoredConstraints = new AutoML.Constraint[0];  // whether or not to ignore the max_models/max_runtime constraints
    protected String _description;
    protected Work _work;
    private final transient List<Consumer<Job>> _onDone = new ArrayList<>();

    StepDefinition _fromDef;
    transient final Predicate<Work> _isSamePriorityGroup = w -> w._priorityGroup == _priorityGroup;

    protected ModelingStep(String provider, IAlgo algo, String id, int priorityGroup, int weight, AutoML autoML) {
        assert priorityGroup >= 0;
        _provider = provider;
        _algo = algo;
        _id = id;
        _priorityGroup = priorityGroup;
        _weight = weight;
        _aml = autoML;
        _description = provider+" "+id;
    }

    /**
     * Each provider (usually one class) defining a collection of steps must have a unique name.
     * @return the name of the provider (usually simply the name of an algo) defining this step. 
     */
    public String getProvider() {
        return _provider;
    }

    /**
     * @return the step identifier: should be unique inside its provider.
     */
    public String getId() {
        return _id;
    }

    /**
     * @return a string that identifies the step uniquely among all steps defined by all providers.
     */
    public String getGlobalId() {
        return _provider+":"+_id;
    }
    
    public IAlgo getAlgo() {
        return _algo;
    }
    
    public int getWeight() {
        return _weight;
    }
    
    public int getPriorityGroup() {
        return _priorityGroup;
    }

    public boolean isResumable() {
        return false;
    }
    
    public boolean ignores(AutoML.Constraint constraint) {
        return ArrayUtils.contains(_ignoredConstraints, constraint);
    }
    
    public boolean limitModelTrainingTime() {
      // if max_models is used, then the global time limit should have no impact on model training budget due to reproducibility concerns.
      return !ignores(AutoML.Constraint.TIMEOUT) && aml().getBuildSpec().build_control.stopping_criteria.max_models() == 0;
    }

    /**
     * @return true iff we can call {@link #run()} on this modeling step to start a new job.
     */
    public boolean canRun() {
        Work work = getAllocatedWork();
        return work != null && work._weight > 0;
    }

    /**
     * Execute this modeling step, returning the job associated to it if any.
     * @return
     */
    public Job run() {
        Job job = startJob();
        if (job != null && job._result != null) {
            register(job._result);
            if (isResumable()) aml().session().addResumableKey(job._result);
        }
        return job;
    }

    /**
     * @return an {@link Iterator} for the potential sub-steps provided by this modeling step.
     */
    public Iterator<? extends ModelingStep> iterateSubSteps() {
        return Collections.emptyIterator();
    }
    
    /**
     * @param id
     * @return the sub-step (if any) with the given identifier, or null if there's no sub-step 
     */
    protected Optional<? extends ModelingStep> getSubStep(String id) {
        return Optional.empty();
    }


    protected abstract JobType getJobType();
    
    /**
     * Starts a new {@link Job} as part of this step.
     * @return the newly started job.
     */
    protected abstract Job startJob();

    protected void onDone(Job job) {
        for (Consumer<Job> exec : _onDone) {
            exec.accept(job);
        }
        _onDone.clear();
    }

    protected void register(Key key) {
        aml().session().registerKeySource(key, this);
    }

    protected AutoML aml() {
        return _aml;
    }

    /**
     * @return the total work allocated for this step.
     */
    protected Work getAllocatedWork() {
        if (_work == null) {
            _work = getWorkAllocations().getAllocation(_id, _algo);
        }
        return _work;
    }

    /**
     * Creates the {@link Work} instance representing the total work handled by this step.
     * @return
     */
    protected Work makeWork() {
        return new Work(getId(), getAlgo(), getJobType(), getPriorityGroup(), getWeight());
    }
    
    protected Key makeKey(String name, boolean withCounter) {
        return aml().makeKey(name, null, withCounter);
    }

    protected WorkAllocations getWorkAllocations() {
        return aml()._workAllocations;
    }

    /**
     * @return the models trained until now, sorted by the default leaderboard metric.
     */
    protected Model[] getTrainedModels() {
        return aml().leaderboard().getModels();
    }

    protected Key<Model>[] getTrainedModelsKeys() {
        return aml().leaderboard().getModelKeys();
    }
    
    protected boolean isCVEnabled() {
        return aml().isCVEnabled();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModelingStep<?> that = (ModelingStep<?>) o;
        return _provider.equals(that._provider) && _id.equals(that._id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_provider, _id);
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
        params._custom_metric_func = buildSpec.build_control.custom_metric_func;

        params._export_checkpoints_dir = buildSpec.build_control.export_checkpoints_dir;
        
        /** Using _main_model_time_budget_factor to determine if and how we should restrict the time for the main model.
         *  Value 0 means do not use time constraint for the main model.
         *  More details in {@link ModelBuilder#setMaxRuntimeSecsForMainModel()}.
         */
        params._main_model_time_budget_factor = 2;
    }
    
    protected void setCrossValidationParams(Model.Parameters params) {
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        params._keep_cross_validation_predictions = aml().getBlendingFrame() == null || buildSpec.build_control.keep_cross_validation_predictions;
        params._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
        params._keep_cross_validation_fold_assignment = buildSpec.build_control.nfolds != 0 && buildSpec.build_control.keep_cross_validation_fold_assignment;
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
            PreprocessingStep.Completer complete = preprocessingStep.apply(params, getPreprocessingConfig());
            _onDone.add(j -> complete.run());
        }
    }
    
    protected Model.Parameters applyPipeline(Key resultKey, Model.Parameters params, Map<String, Object[]> hyperParams) {
      if (aml().getPipelineParams() == null) return params;
      PipelineParameters pparams = (PipelineParameters) aml().getPipelineParams().clone();
      setCommonModelBuilderParams(pparams);
      pparams._seed = params._seed;
      pparams._max_runtime_secs = params._max_runtime_secs;
      pparams._estimatorParams = params;
      pparams._estimatorKeyGen = hyperParams == null 
              ? new ConstantKeyGen(resultKey) 
              : new PatternKeyGen("{0}|s/"+PIPELINE_KEY_PREFIX+"//")  // in case of grid, remove the Pipeline prefix to obtain the estimator key, this allows naming compatibility with the classic mode.
              ;
      if (hyperParams != null) {
        Map<String, Object[]> pipelineHyperParams = new HashMap<>();
        for (Map.Entry<String, Object[]> e : hyperParams.entrySet()) {
          pipelineHyperParams.put("estimator."+e.getKey(), e.getValue());
        }
        hyperParams.clear();
        hyperParams.putAll(pipelineHyperParams);
        hyperParams.putAll(aml().getPipelineHyperParams());
      }
      return pparams;
    }
    
    protected PreprocessingConfig getPreprocessingConfig() {
        return new PreprocessingConfig();
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
            parms._stopping_metric = aml().getResponseColumn().cardinality() == -1 ? StoppingMetric.deviance : StoppingMetric.logloss;
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
     * Step designed to build a single/default model.
     */
    public static abstract class ModelStep<M extends Model> extends ModelingStep<M> {

        public static final int DEFAULT_MODEL_TRAINING_WEIGHT = 10;
        public static final int DEFAULT_MODEL_GROUP = 1;

        public ModelStep(String provider, IAlgo algo, String id, AutoML autoML) {
            this(provider, algo, id, DEFAULT_MODEL_GROUP, DEFAULT_MODEL_TRAINING_WEIGHT, autoML);
        }
        
        public ModelStep(String provider, IAlgo algo, String id, int priorityGroup, int weight, AutoML autoML) {
            super(provider, algo, id, priorityGroup, weight, autoML);
        }

        @Override
        protected JobType getJobType() {
            return JobType.ModelBuild;
        }

        public abstract Model.Parameters prepareModelParameters();

        @Override
        protected Job<M> startJob() {
            return trainModel(prepareModelParameters());
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
            setDistributionParameters(parms);

            // override model's max_runtime_secs to ensure that the total max_runtime doesn't exceed expectations
            if (limitModelTrainingTime()) {
                Work work = getAllocatedWork();
//                double maxAssignedTimeSecs = aml().timeRemainingMs() / 1e3; // legacy
//                double maxAssignedTimeSecs = aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3; //including default models in the distribution of the time budget.
//                double maxAssignedTimeSecs = aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work, isDefaultModel) / 1e3; //PUBDEV-7595
                double maxAssignedTimeSecs = aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work, _isSamePriorityGroup) / 1e3; // Models from a priority group + SEs
                parms._max_runtime_secs = parms._max_runtime_secs == 0 ? maxAssignedTimeSecs
                        : Math.min(parms._max_runtime_secs, maxAssignedTimeSecs);
            } else {
              parms._max_runtime_secs = 0;
            }
            Log.debug("Training model: " + algoName + ", time remaining (ms): " + aml().timeRemainingMs());
            aml().eventLog().debug(Stage.ModelTraining, parms._max_runtime_secs == 0
                    ? "No time limitation for "+key
                    : "Time assigned for "+key+": "+parms._max_runtime_secs+"s");
            return startModel(key, parms);
        }
    }

    /**
     * Step designed to build multiple models using a (random) grid search.
     */
    public static abstract class GridStep<M extends Model> extends ModelingStep<M> {

        public static final int DEFAULT_GRID_TRAINING_WEIGHT = 30;
        public static final int DEFAULT_GRID_GROUP = 2;
        protected static final int GRID_STOPPING_ROUND_FACTOR = 2;

        public GridStep(String provider, IAlgo algo, String id, AutoML autoML) {
            this(provider, algo, id, DEFAULT_GRID_GROUP, DEFAULT_GRID_TRAINING_WEIGHT, autoML);
        }
        
        public GridStep(String provider, IAlgo algo, String id, int priorityGroup, int weight, AutoML autoML) {
            super(provider, algo, id, priorityGroup, weight, autoML);
        }

        @Override
        protected JobType getJobType() {
            return JobType.HyperparamSearch;
        }

        @Override
        public boolean isResumable() {
            return true;
        }

        public abstract Model.Parameters prepareModelParameters();
        
        public abstract Map<String, Object[]> prepareSearchParameters();

        @Override
        protected Job<Grid> startJob() {
            return hyperparameterSearch(prepareModelParameters(), prepareSearchParameters());
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
                aml().eventLog().error(Stage.ModelTraining, "Internal error doing hyperparameter search");
                throw new H2OIllegalArgumentException("Hyperparameter search can't create a new instance of Model.Parameters subclass: " + baseParms.getClass());
            }

            initTimeConstraints(baseParms, 0);
            setCommonModelBuilderParams(baseParms);
            // grid seed is provided later through the searchCriteria
            setStoppingCriteria(baseParms, defaults);
            setCustomParams(baseParms);
            setDistributionParameters(baseParms);

            AutoMLBuildSpec buildSpec = aml().getBuildSpec();
            RandomDiscreteValueSearchCriteria searchCriteria = (RandomDiscreteValueSearchCriteria) buildSpec.build_control.stopping_criteria.getSearchCriteria().clone();
            setSearchCriteria(searchCriteria, baseParms);

            if (null == key) key = makeKey(_provider, true);

            Log.debug("Hyperparameter search: " + _provider + ", time remaining (ms): " + aml().timeRemainingMs());
            aml().eventLog().debug(Stage.ModelTraining, searchCriteria.max_runtime_secs() == 0
                    ? "No time limitation for " + key
                    : "Time assigned for " + key + ": " + searchCriteria.max_runtime_secs() + "s");
            return startSearch(
                    key,
                    baseParms,
                    searchParms,
                    searchCriteria
            );
        }
        
        protected void setSearchCriteria(RandomDiscreteValueSearchCriteria searchCriteria, Model.Parameters baseParms) {
            Work work = getAllocatedWork();
            // for time limit, this is allocated in proportion of the entire work budget.
            double maxAssignedTimeSecs = limitModelTrainingTime() 
                    ? aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work, _isSamePriorityGroup) / 1e3
                    : 0;
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

            searchCriteria.set_stopping_rounds(baseParms._stopping_rounds * GRID_STOPPING_ROUND_FACTOR);
        }

    }

    /**
     * Step designed to train some models (or not) and then deciding to make a selection
     * and add and/or remove models to/from the current leaderboard.
     */
    public static abstract class SelectionStep<M extends Model> extends ModelingStep<M> {

        public static final int DEFAULT_SELECTION_TRAINING_WEIGHT = 20;
        public static final int DEFAULT_SELECTION_GROUP = 3;

        public SelectionStep(String provider, IAlgo algo, String id, AutoML autoML) {
            this(provider, algo, id, DEFAULT_SELECTION_GROUP, DEFAULT_SELECTION_TRAINING_WEIGHT, autoML);
        }
        
        public SelectionStep(String provider, IAlgo algo, String id, int priorityGroup, int weight, AutoML autoML) {
            super(provider, algo, id, priorityGroup, weight, autoML);
        }

        @Override
        protected JobType getJobType() {
            return JobType.Selection;
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
                    tmpEventLog.asLogger(Stage.ModelTraining),
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
            Key<Models> key = makeKey(_provider+"_"+_id, false);
            aml().trackKeys(key);
            Job<Models> job = new Job<>(key, Models.class.getName(), _description);
            Work work = getAllocatedWork();

            double maxAssignedTimeSecs = limitModelTrainingTime()
                    ? aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3
                    : 0;

            aml().eventLog().debug(Stage.ModelTraining, maxAssignedTimeSecs == 0
                    ? "No time limitation for "+key
                    : "Time assigned for "+key+": "+maxAssignedTimeSecs+"s");

            return job.start(new H2O.H2OCountedCompleter() {

                final Models result = new Models(key, Model.class, job);
                final Key<Models> selectionKey = Key.make(key+"_select");
                final EventLog selectionEventLog = EventLog.getOrMake(selectionKey);
//                EventLog selectionEventLog = aml().eventLog();
                final LeaderboardHolder selectionLeaderboard = makeLeaderboard(selectionKey.toString(), selectionEventLog);

                {
                    result.delete_and_lock(job);
                }

                @Override
                public void compute2() {
                    Countdown countdown = Countdown.fromSeconds(maxAssignedTimeSecs);
                    Selection selection = null;
                    try {
                        ModelingStepsExecutor localExecutor = new ModelingStepsExecutor(selectionLeaderboard.get(), selectionEventLog, countdown);
                        localExecutor.start();
                        Job<Models> innerTraining = startTraining(selectionKey, maxAssignedTimeSecs);
                        StepResultState state = localExecutor.monitor(innerTraining, SelectionStep.this, job);

                        if (state.is(ResultStatus.success)) {
                            Log.debug("Selection leaderboard "+selectionLeaderboard.get()._key, selectionLeaderboard.get().toLogString());
                            selection = getSelectionStrategy().select(trainedModelKeys, selectionLeaderboard.get().getModelKeys());
                            Leaderboard lb = aml().leaderboard();
                            Log.debug("Selection result for job "+key, ToStringBuilder.reflectionToString(selection));
                            lb.removeModels(selection._remove, false); // do remove the model immediately from DKV: if it were part of a grid, it prevents the grid from being resumed.
                            aml().trackKeys(selection._remove);
                            lb.addModels(selection._add);
                        } else if (state.is(ResultStatus.failed)) {
                            throw (RuntimeException)state.error();
                        } else if (state.is(ResultStatus.cancelled)) {
                            throw new Job.JobCancelledException(innerTraining);
                        }
                    } finally {
                        result.unlock(job);
                        if (selection != null) {
                            result.addModels(selection._add);
                        }
                    }
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

                final Models models = new Models(result, Model.class, jModels);
                {
                    models.delete_and_lock(jModels);
                }

                @Override
                public void compute2() {
                    ModelingStepsExecutor.ensureStopRequestPropagated(job, jModels, ModelingStepsExecutor.DEFAULT_POLLING_INTERVAL_IN_MILLIS);
                    Keyed res = job.get();
                    models.unlock(jModels);
                    if (res instanceof Model) {
                        models.addModel(res.getKey());
                    } else if (res instanceof ModelContainer) {
                        models.addModels(((ModelContainer) res).getModelKeys());
                        res.remove(false);
                    } else if (res == null && jModels.stop_requested()) {
                        // Do nothing - stop was requested before we managed to train any model
                    } else {
                        throw new H2OIllegalArgumentException("Can only convert jobs producing a single Model or ModelContainer.");
                    }
                    tryComplete();
                }
            }, job._work, job._max_runtime_msecs);
        }
    }


    /**
     * Step designed to dynamically choose to train a model or another, a grid or anything else,
     * based on the current automl workflow history.
     */
    public static abstract class DynamicStep<M extends Model> extends ModelingStep<M> {
        
        public static final int DEFAULT_DYNAMIC_TRAINING_WEIGHT = 20;
        public static final int DEFAULT_DYNAMIC_GROUP = 100;

        public static class VirtualAlgo implements IAlgo {

            public VirtualAlgo() {}
            
            @Override
            public String name() {
                return "virtual";
            }
        }
        
        private transient Collection<ModelingStep> _subSteps;

        public DynamicStep(String provider, String id, AutoML autoML) {
            this(provider, id, DEFAULT_DYNAMIC_GROUP, DEFAULT_DYNAMIC_TRAINING_WEIGHT, autoML);
        }
        
        public DynamicStep(String provider, String id, int priorityGroup, int weight, AutoML autoML) {
            super(provider, new VirtualAlgo(), id, priorityGroup, weight, autoML);
        }

        @Override
        public boolean canRun() {
            // this step is designed to delegate its work to sub-steps by default, 
            // so the parent step itself has nothing to run.
            return false;
        }
        
        @Override
        protected Job<M> startJob() {
            // see comment in canRun().
            return null;
        }
        
        @Override
        protected JobType getJobType() {
            return JobType.Dynamic;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Key<Models> makeKey(String name, boolean withCounter) {
            return aml().makeKey(name, "decision", withCounter);
        }
        
        private void initSubSteps() {
            if (_subSteps == null) {
                _subSteps = prepareModelingSteps();
            }
        }

        @Override
        public Iterator<? extends ModelingStep> iterateSubSteps() {
            initSubSteps();
            return _subSteps.iterator();
        }

        @Override
        protected Optional<? extends ModelingStep> getSubStep(String id) {
            initSubSteps();
            return _subSteps.stream()
                    .filter(step -> step._id.equals(id))
                    .findFirst();
        }

        protected abstract Collection<ModelingStep> prepareModelingSteps();
    }

}
