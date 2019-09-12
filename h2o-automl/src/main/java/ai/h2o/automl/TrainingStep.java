package ai.h2o.automl;

import ai.h2o.automl.WorkAllocations.JobType;
import ai.h2o.automl.WorkAllocations.Work;
import hex.Model;
import hex.ModelBuilder;
import hex.ScoreKeeper;
import hex.ensemble.StackedEnsembleModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import water.Iced;
import water.Job;
import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.util.Log;
import water.util.fp.Predicate;

import java.util.Map;

public abstract class TrainingStep<M extends Model> extends Iced<TrainingStep> {

    protected Algo _algo;
    protected String _id;
    protected AutoML _aml;
    protected boolean _ignoreConstraints;  // whether or not to ignore the max_models/max_runtime constraints
    protected String _description;

    protected TrainingStep(Algo algo, String id, AutoML autoML) {
        _algo = algo;
        _id = id;
        _aml = autoML;
        _description = algo.name()+" "+id;
    }

    protected abstract Work getWork();

    protected abstract Key makeKey(String name, boolean withCounter);

    protected abstract Job makeJob();

    protected boolean canRun() {
        return getWork() != null;
    }

    protected WorkAllocations getWorkAllocations() {
        return _aml.workAllocations;
    }

    protected Model[] getTrainedModels() {
        return _aml.leaderboard().getModels();
    }

    protected boolean isCVEnabled() {
        return _aml.isCVEnabled();
    }

    void setCommonModelBuilderParams(Model.Parameters params) {
        params._train = _aml.trainingFrame._key;
        if (null != _aml.validationFrame)
            params._valid = _aml.validationFrame._key;

        AutoMLBuildSpec buildSpec = _aml.getBuildSpec();
        params._response_column = buildSpec.input_spec.response_column;
        params._ignored_columns = buildSpec.input_spec.ignored_columns;

        // currently required, for the base_models, for stacking:
        if (! (params instanceof StackedEnsembleModel.StackedEnsembleParameters)) {
            params._keep_cross_validation_predictions = _aml.getBlendingFrame() == null ? true : buildSpec.build_control.keep_cross_validation_predictions;

            // TODO: StackedEnsemble doesn't support weights yet in score0
            params._fold_column = buildSpec.input_spec.fold_column;
            params._weights_column = buildSpec.input_spec.weights_column;

            if (buildSpec.input_spec.fold_column == null) {
                params._nfolds = buildSpec.build_control.nfolds;
                if (buildSpec.build_control.nfolds > 1) {
                    // TODO: below allow the user to specify this (vs Modulo)
                    params._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
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

    void setStoppingCriteria(Model.Parameters parms, Model.Parameters defaults, boolean isIndividualModel) {
        // If the caller hasn't set ModelBuilder stopping criteria, set it from our global criteria.
        AutoMLBuildSpec buildSpec = _aml.getBuildSpec();
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
            parms._seed = buildSpec.build_control.stopping_criteria.seed() + _aml.individualModelsTrained.getAndIncrement();

        if (parms._stopping_metric == defaults._stopping_metric)
            parms._stopping_metric = buildSpec.build_control.stopping_criteria.stopping_metric();

        if (parms._stopping_metric == ScoreKeeper.StoppingMetric.AUTO) {
            String sort_metric = getSortMetric();
            parms._stopping_metric = sort_metric == null ? ScoreKeeper.StoppingMetric.AUTO
                    : sort_metric.equals("auc") ? ScoreKeeper.StoppingMetric.logloss
                    : metricValueOf(sort_metric);
        }

        if (parms._stopping_rounds == defaults._stopping_rounds)
            parms._stopping_rounds = buildSpec.build_control.stopping_criteria.stopping_rounds();

        if (parms._stopping_tolerance == defaults._stopping_tolerance)
            parms._stopping_tolerance = buildSpec.build_control.stopping_criteria.stopping_tolerance();
    }

    private String getSortMetric() {
        //ensures that the sort metric is always updated according to the defaults set by leaderboard
        Leaderboard leaderboard = _aml.leaderboard();
        return leaderboard == null ? null : leaderboard.sort_metric;
    }

    private static ScoreKeeper.StoppingMetric metricValueOf(String name) {
        if (name == null) return ScoreKeeper.StoppingMetric.AUTO;
        switch (name) {
            case "mean_residual_deviance": return ScoreKeeper.StoppingMetric.deviance;
            default:
                String[] attempts = { name, name.toUpperCase(), name.toLowerCase() };
                for (String attempt : attempts) {
                    try {
                        return ScoreKeeper.StoppingMetric.valueOf(attempt);
                    } catch (IllegalArgumentException ignored) { }
                }
                return ScoreKeeper.StoppingMetric.AUTO;
        }
    }


    public static abstract class ModelStep<M extends Model> extends TrainingStep<M> {

        public ModelStep(Algo algo, String id, AutoML autoML) {
            super(algo, id, autoML);
        }

        @Override
        protected abstract Job<M> makeJob();

        @Override
        protected Work getWork() {
            return getWorkAllocations().getAllocation(_algo, JobType.ModelBuild);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected Key<M> makeKey(String name, boolean withCounter) {
            return (Key<M>)_aml.modelKey(name, withCounter);
        }

        protected Job<M> trainModel(Model.Parameters parms) {
            return trainModel(null, parms);
        }

        /**
         * @param key (optional) model key
         * @param parms the model builder params
         * @return a started training model
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

            // override model's max_runtime_secs to ensure that the total max_runtime doesn't exceed expectations
            if (_ignoreConstraints)
                builder._parms._max_runtime_secs = 0;
            else if (builder._parms._max_runtime_secs == 0)
                builder._parms._max_runtime_secs = _aml.timeRemainingMs() / 1e3;
            else
                builder._parms._max_runtime_secs = Math.min(builder._parms._max_runtime_secs, _aml.timeRemainingMs() / 1e3);

            builder.init(false);          // validate parameters

            Log.debug("Training model: " + algoName + ", time remaining (ms): " + _aml.timeRemainingMs());
            try {
                return builder.trainModelOnH2ONode();
            } catch (H2OIllegalArgumentException exception) {
                _aml.eventLog().warn(EventLogEntry.Stage.ModelTraining, "Skipping training of model "+key+" due to exception: "+exception);
                return null;
            }
        }

    }

    public static abstract class GridStep<M extends Model> extends TrainingStep<M> {
        public GridStep(Algo algo, String id, AutoML autoML) {
            super(algo, id, autoML);
        }

        @Override
        protected abstract Job<Grid> makeJob();

        @Override
        protected Work getWork() {
            return getWorkAllocations().getAllocation(_algo, JobType.HyperparamSearch);
        }

        @Override
        protected Key<Grid> makeKey(String name, boolean withCounter) {
            return _aml.gridKey(name, withCounter);
        }

        protected Job<Grid> hyperparameterSearch(Model.Parameters baseParms, Map<String, Object[]> searchParms) {
            return hyperparameterSearch(null, baseParms, searchParms);
        }

        /**
         * Do a random hyperparameter search.  Caller must eventually do a <i>get()</i>
         * on the returned Job to ensure that it's complete.
         * @param gridKey optional grid key
         * @param baseParms ModelBuilder parameter values that are common across all models in the search
         * @param searchParms hyperparameter search space
         * @return the started hyperparameter search job
         */
        protected Job<Grid> hyperparameterSearch(Key<Grid> gridKey, Model.Parameters baseParms, Map<String, Object[]> searchParms) {
            Model.Parameters defaults;
            try {
                defaults = baseParms.getClass().newInstance();
            } catch (Exception e) {
                _aml.eventLog().warn(EventLogEntry.Stage.ModelTraining, "Internal error doing hyperparameter search");
                throw new H2OIllegalArgumentException("Hyperparameter search can't create a new instance of Model.Parameters subclass: " + baseParms.getClass());
            }

            setCommonModelBuilderParams(baseParms);
            setStoppingCriteria(baseParms, defaults, false);

            AutoMLBuildSpec buildSpec = _aml.getBuildSpec();
            HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria =
                    (HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria) buildSpec.build_control.stopping_criteria.getSearchCriteria().clone();
            Work work = getWork();
            double maxAssignedTime = _aml.timeRemainingMs() * getWorkAllocations().remainingWorkRatio(work) / 1e3;
            // predicate can be removed if/when we decide to include SEs in the max_models limit
            int maxAssignedModels = (int) Math.ceil(_aml.remainingModels() * getWorkAllocations().remainingWorkRatio(work, new Predicate<Work>() {
                @Override
                public Boolean apply(Work work) {
                    return work.algo != Algo.StackedEnsemble;
                }
            }));

            if (searchCriteria.max_runtime_secs() == 0)
                searchCriteria.set_max_runtime_secs(maxAssignedTime);
            else
                searchCriteria.set_max_runtime_secs(Math.min(searchCriteria.max_runtime_secs(), maxAssignedTime));

            if (searchCriteria.max_models() == 0)
                searchCriteria.set_max_models(maxAssignedModels);
            else
                searchCriteria.set_max_models(Math.min(searchCriteria.max_models(), maxAssignedModels));

            _aml.eventLog().info(EventLogEntry.Stage.ModelTraining, "AutoML: starting "+_algo+" hyperparameter search");

            if (null == gridKey) gridKey = makeKey(_algo.name(), true);
            _aml.addGridKey(gridKey);
            Log.debug("Hyperparameter search: "+_algo.name()+", time remaining (ms): "+_aml.timeRemainingMs());
            return GridSearch.startGridSearch(
                    gridKey,
                    baseParms,
                    searchParms,
                    new GridSearch.SimpleParametersBuilderFactory<>(),
                    searchCriteria
            );
        }
    }
}
