package ai.h2o.automl;

import ai.h2o.automl.AutoMLBuildSpec.AutoMLCustomParameters;
import ai.h2o.automl.events.EventLog;
import ai.h2o.automl.events.EventLogEntry.Stage;
import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.leaderboard.Leaderboard;
import hex.Model;
import hex.Model.Parameters.FoldAssignmentScheme;
import hex.ModelBuilder;
import hex.ModelContainer;
import hex.ScoreKeeper.StoppingMetric;
import hex.ensemble.StackedEnsembleModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.EnumUtils;
import water.util.Log;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Parent class defining common properties and common logic for actual {@link AutoML} training steps.
 */
public abstract class ModelingStep<M extends Model> extends Iced<ModelingStep> {

    private transient AutoML _aml;

    protected final Algo _algo;
    protected final String _id;
    protected int _weight;
    protected boolean _ignoreConstraints;  // whether or not to ignore the max_models/max_runtime constraints
    protected String _description;

    StepDefinition _fromDef;

    protected ModelingStep(Algo algo, String id, int weight, AutoML autoML) {
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

    protected AutoML aml() {
        return _aml;
    }

    protected boolean canRun() {
        return getAllocatedWork() != null;
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
    void setCommonModelBuilderParams(Model.Parameters params) {
        params._train = aml()._trainingFrame._key;
        if (null != aml()._validationFrame)
            params._valid = aml()._validationFrame._key;

        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        params._response_column = buildSpec.input_spec.response_column;
        params._ignored_columns = buildSpec.input_spec.ignored_columns;

        // currently required, for the base_models, for stacking:
        if (! (params instanceof StackedEnsembleModel.StackedEnsembleParameters)) {  // TODO: now that we have an object hierarchy, we can think about refactoring this logic
            params._keep_cross_validation_predictions = aml().getBlendingFrame() == null ? true : buildSpec.build_control.keep_cross_validation_predictions;

            // TODO: StackedEnsemble doesn't support weights yet in score0
            params._fold_column = buildSpec.input_spec.fold_column;
            params._weights_column = buildSpec.input_spec.weights_column;

            if (buildSpec.input_spec.fold_column == null) {
                params._nfolds = buildSpec.build_control.nfolds;
                if (buildSpec.build_control.nfolds > 1) {
                    // TODO: below allow the user to specify this (vs Modulo)
                    params._fold_assignment = FoldAssignmentScheme.Modulo;
                }
            }
            if (buildSpec.build_control.balance_classes) {
                params._balance_classes = buildSpec.build_control.balance_classes;
                params._class_sampling_factors = buildSpec.build_control.class_sampling_factors;
                params._max_after_balance_size = buildSpec.build_control.max_after_balance_size;
            }
            //TODO: add a check that gives an error when class_sampling_factors, max_after_balance_size is set and balance_classes = false
        }

        params._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
        params._keep_cross_validation_fold_assignment = buildSpec.build_control.nfolds != 0 && buildSpec.build_control.keep_cross_validation_fold_assignment;
        params._export_checkpoints_dir = buildSpec.build_control.export_checkpoints_dir;
    }

    void setCustomParams(Model.Parameters params) {
        AutoMLCustomParameters customParams = aml().getBuildSpec().build_models.algo_parameters;
        if (customParams == null) return;
        customParams.applyCustomParameters(_algo, params);
    }


    /**
     * Configures early-stopping for the model or set of models to be built.
     *
     * @param parms the model parameters to which the stopping criteria will be added.
     * @param defaults the default parameters for the corresponding {@link ModelBuilder}.
     * @param isIndividualModel is the parms will be use to build a single model or for hyperparameter search.
     */
    void setStoppingCriteria(Model.Parameters parms, Model.Parameters defaults, boolean isIndividualModel) {
        // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.
        AutoMLBuildSpec buildSpec = aml().getBuildSpec();
        parms._max_runtime_secs = buildSpec.build_control.stopping_criteria.max_runtime_secs_per_model();

        //FIXME: Do we really need to compare with defaults before setting the buildSpec value instead?
        // This can create subtle bugs: e.g. if dev wanted to enforce a stopping criteria for a specific algo/model,
        // he wouldn't be able to enforce the default value, that would always be overridden by buildSpec.
        // We should instead provide hooks and ensure that properties are always set in the following order:
        //  1. defaults, 2. user defined, 3. internal logic/algo specific based on the previous state (esp. handling of AUTO properties).

        // If we have set a seed for the search and not for the individual model params
        // then use a sequence starting with the same seed given for the model build.
        // Don't use the same exact seed so that, e.g., if we build two GBMs they don't
        // do the same row and column sampling.
        // Leave it as is for Grids as HyperSpaceWalker has its own increment logic.
        if (isIndividualModel && parms._seed == defaults._seed && buildSpec.build_control.stopping_criteria.seed() != -1)
            parms._seed = buildSpec.build_control.stopping_criteria.seed() + aml().individualModelsTrained.getAndIncrement();

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

        public ModelStep(Algo algo, String id, int cost, AutoML autoML) {
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

        @SuppressWarnings("unchecked")
        @Override
        protected Key<M> makeKey(String name, boolean withCounter) {
            return (Key<M>)aml().modelKey(name, withCounter);
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

            Job<M> job = new Job<>(key, ModelBuilder.javaName(_algo.urlName()), _description);
            ModelBuilder builder = ModelBuilder.make(_algo.urlName(), job, (Key<Model>)key);
            Model.Parameters defaults = builder._parms;
            builder._parms = parms;

            setCommonModelBuilderParams(builder._parms);
            setStoppingCriteria(builder._parms, defaults, true);
            setCustomParams(builder._parms);

            // override model's max_runtime_secs to ensure that the total max_runtime doesn't exceed expectations
            if (_ignoreConstraints)
                builder._parms._max_runtime_secs = 0;
            else if (builder._parms._max_runtime_secs == 0)
                builder._parms._max_runtime_secs = aml().timeRemainingMs() / 1e3;
            else
                builder._parms._max_runtime_secs = Math.min(builder._parms._max_runtime_secs, aml().timeRemainingMs() / 1e3);

            aml().eventLog().info(Stage.ModelTraining, "AutoML: starting "+key+" model training");
            builder.init(false);          // validate parameters
            Log.debug("Training model: " + algoName + ", time remaining (ms): " + aml().timeRemainingMs());
            try {
                return builder.trainModelOnH2ONode();
            } catch (H2OIllegalArgumentException exception) {
                aml().eventLog().warn(Stage.ModelTraining, "Skipping training of model "+key+" due to exception: "+exception);
                return null;
            }
        }

    }

    /**
     * Convenient base class for steps defining a (random) grid search.
     */
    public static abstract class GridStep<M extends Model> extends ModelingStep<M> {

        public static final int DEFAULT_GRID_TRAINING_WEIGHT = 20;

        public GridStep(Algo algo, String id, int cost, AutoML autoML) {
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
        protected Key<Grid> makeKey(String name, boolean withCounter) {
            return aml().gridKey(name, withCounter);
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

            setCommonModelBuilderParams(baseParms);
            setStoppingCriteria(baseParms, defaults, false);
            setCustomParams(baseParms);

            AutoMLBuildSpec buildSpec = aml().getBuildSpec();
            RandomDiscreteValueSearchCriteria searchCriteria = (RandomDiscreteValueSearchCriteria)buildSpec.build_control.stopping_criteria.getSearchCriteria().clone();

            Work work = getAllocatedWork();
            double maxAssignedTimeSecs = aml().timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3;
            // predicate can be removed if/when we decide to include SEs in the max_models limit
            int maxAssignedModels = (int) Math.ceil(aml().remainingModels() * getWorkAllocations().remainingWorkRatio(work, w -> w._algo != Algo.StackedEnsemble));

            if (searchCriteria.max_runtime_secs() == 0)
                searchCriteria.set_max_runtime_secs(maxAssignedTimeSecs);
            else
                searchCriteria.set_max_runtime_secs(Math.min(searchCriteria.max_runtime_secs(), maxAssignedTimeSecs));

            if (searchCriteria.max_models() == 0)
                searchCriteria.set_max_models(maxAssignedModels);
            else
                searchCriteria.set_max_models(Math.min(searchCriteria.max_models(), maxAssignedModels));

            if (null == key) key = makeKey(_algo.name(), true);
            aml().addGridKey(key);

            aml().eventLog().info(Stage.ModelTraining, "AutoML: starting "+key+" hyperparameter search");
            Log.debug("Hyperparameter search: "+_algo.name()+", time remaining (ms): "+aml().timeRemainingMs());
            return GridSearch.startGridSearch(
                    key,
                    baseParms,
                    searchParms,
                    new GridSearch.SimpleParametersBuilderFactory<>(),
                    searchCriteria,
                    GridSearch.SEQUENTIAL_MODEL_BUILDING
            );
        }
    }

    public static abstract class SelectionStep<M extends Model> extends ModelingStep<M> {

        public SelectionStep(Algo algo, String id, int weight, AutoML autoML) {
            super(algo, id, weight, autoML);
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
        protected Key<Models> makeKey(String name, boolean withCounter) {
            return (Key)aml().modelKey(name, withCounter); //TODO: key dedicated to Models collection
        }

        protected Leaderboard makeTmpLeaderboard() {
            Leaderboard amlLeaderboard = aml().leaderboard();
            Leaderboard tmpLeaderboard = new Leaderboard(
                    "tmp_leaderboard_"+_algo+"_"+_id,
                    new EventLog(Key.make("tmp_eventlog_"+_algo+"_"+_id)),
                    amlLeaderboard.leaderboardFrame(),
                    amlLeaderboard.getSortMetric()
            );
            return tmpLeaderboard;
        }

        @Override
        protected Job<Models> startJob() {
            Key<Model>[] trainedModelKeys = getTrainedModelsKeys();
            Key<Models> key = makeKey(_id, false);
            Job<Models> job = new Job<>(key, Models.class.getName(), _description);
            int work = 17; //TODO
            int maxRuntimeSecs = 42; //TODO
            return job.start(new H2O.H2OCountedCompleter() {
                @Override
                public void compute2() {
                    Models tmpModels = startTraining(key).get();
                    // monitor?
                    ModelSelectionStrategy.Selection selection = getSelectionStrategy().select(trainedModelKeys, tmpModels._modelKeys);
                    Leaderboard lb = aml().leaderboard();
                    lb.addModels(selection._add);
                    lb.removeModels(selection._remove);
                    Models result = new Models(key, Model.class);
                    result.addModels(selection._add);
                    DKV.put(result);
                }
            }, work, maxRuntimeSecs);
        }

        protected abstract Job<Models> startTraining(Key<Models> result);

        protected abstract ModelSelectionStrategy getSelectionStrategy();

        protected Job<Models> asModelsJob(Job j ){
            Job<Models> jModels = new Job<>(j._result, Models.class.getName(), j._description); // can use the same result key as original job, as it is dropped once its result is read
            return jModels.start(new H2O.H2OCountedCompleter() {
                @Override
                public void compute2() {
                    Object res = j.get();
                    Models models = new Models(j._result, Model.class, jModels);
                    if (res instanceof Model) {
                        models.addModel(((Model)res)._key);
                    } else if (res instanceof ModelContainer) {
                        models.addModels(((ModelContainer)res).getModelKeys());
                    } else {
                        throw new H2OIllegalArgumentException("Can only convert jobs producing a single Model or ModelContainer.");
                    }
                }
            }, j._work, j._max_runtime_msecs);
        }
    }

    public static class Models<M extends Model> extends Lockable<Models<M>> implements ModelContainer<M> {

        private final Class<M> _clz;
        private final Job _job;
        private Key<M>[] _modelKeys;

        public Models(Key<Models<M>> key, Class<M> clz) {
            this(key, clz, null);
        }

        public Models(Key<Models<M>> key, Class<M> clz, Job job) {
            super(key);
            _clz = clz;
            _job = job;
        }

        @Override
        public Key<M>[] getModelKeys() {
            return _modelKeys.clone();
        }

        @Override
        public M[] getModels() {
            M[] models = (M[]) Array.newInstance(_clz, _modelKeys.length);
            for (int i=0; i < _modelKeys.length; i++) {
                Key<M> key = _modelKeys[i];
                models[i] = key == null ? null : key.get() ;
            }
            return models;
        }

        @Override
        public int getModelCount() {
            return _modelKeys.length;
        }

        public void addModel(Key<M> key) {
            addModels(new Key[]{key});
        }

        public void addModels(Key<M>[] keys) {
           write_lock(_job);
           _modelKeys = ArrayUtils.append(_modelKeys, keys);
           update(_job);
           unlock(_job);
        }
    }

    @FunctionalInterface
    public interface ModelSelectionStrategy<M extends Model>{

        class Selection<M extends Model> {
            final Key<M>[] _add;  //models that should be added to the original population
            final Key<M>[] _remove; //models that should be removed from the original population

            public Selection(Key<M>[] add, Key<M>[] remove) {
                _add = add;
                _remove = remove;
            }
        }

        Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels);
    }

    public abstract class LeaderboardBasedSelectionStrategy<M extends Model> implements ModelSelectionStrategy<M> {

        final Supplier<Leaderboard> _leaderboardSupplier;

        public LeaderboardBasedSelectionStrategy(Supplier<Leaderboard> leaderboardSupplier) {
            _leaderboardSupplier = leaderboardSupplier;
        }

        Leaderboard makeTmpLeaderboard() {
            return _leaderboardSupplier.get();
        }
    }

    public class KeepBestN<M extends Model> extends LeaderboardBasedSelectionStrategy<M>{

        private final int _N;

        public KeepBestN(int N, Supplier<Leaderboard> leaderboardSupplier) {
            super(leaderboardSupplier);
            _N = N;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels) {
            Leaderboard tmpLeaderboard = makeTmpLeaderboard();
            tmpLeaderboard.addModels((Key<Model>[]) originalModels);
            tmpLeaderboard.addModels((Key<Model>[]) newModels);
            Key<Model>[] sortedKeys = tmpLeaderboard.getModelKeys();
            Key<Model>[] bestN = ArrayUtils.subarray(sortedKeys, 0, _N);
            Key<M>[] toAdd = Arrays.stream(bestN).filter(k -> !ArrayUtils.contains(originalModels, k)).toArray(Key[]::new);
            Key<M>[] toRemove = Arrays.stream(originalModels).filter(k -> !ArrayUtils.contains(bestN, k)).toArray(Key[]::new);
            return new Selection<>(toAdd, toRemove);
        }
    }

    public class KeepBestConstantSize<M extends Model> extends LeaderboardBasedSelectionStrategy<M> {

        public KeepBestConstantSize(Supplier<Leaderboard> leaderboardSupplier) {
            super(leaderboardSupplier);
        }

        @Override
        public Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels) {
            return new KeepBestN<M>(originalModels.length, _leaderboardSupplier).select(originalModels, newModels);
        }
    }

    public class KeepBestNFromSubgroup<M extends Model> extends LeaderboardBasedSelectionStrategy<M> {

        private final Predicate<Key<M>> _criterion;
        private final int _N;

        public KeepBestNFromSubgroup(int N, Predicate<Key<M>> criterion, Supplier<Leaderboard> leaderboardSupplier) {
            super(leaderboardSupplier);
            _criterion = criterion;
            _N = N;
        }

        @Override
        public Selection<M> select(Key<M>[] originalModels, Key<M>[] newModels) {
            Key<M>[] originalModelsSubgroup = Arrays.stream(originalModels).filter(_criterion).toArray(Key[]::new);
            Key<M>[] newModelsSubGroup = Arrays.stream(newModels).filter(_criterion).toArray(Key[]::new);
            return new KeepBestN<M>(_N, _leaderboardSupplier).select(originalModelsSubgroup, newModelsSubGroup);
        }
    }

}
