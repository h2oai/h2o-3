package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLogEntry;
import hex.KeyValue;
import hex.Model;
import hex.ensemble.Metalearner;
import hex.ensemble.StackedEnsembleModel;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.glm.GLMModel;
import water.DKV;
import water.Job;
import water.Key;
import water.util.PojoUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class StackedEnsembleStepsProvider
        implements ModelingStepsProvider<StackedEnsembleStepsProvider.StackedEnsembleSteps>
                 , ModelParametersProvider<StackedEnsembleParameters> {

    public static class StackedEnsembleSteps extends ModelingSteps {
        @Override
        protected void cleanup() {
            super.cleanup();
            Arrays.stream(aml().leaderboard().getModels())
                    .filter(model -> model instanceof StackedEnsembleModel)
                    .forEach(model -> ((StackedEnsembleModel) model).deleteBaseModelPredictions());
        }

        static final String NAME = Algo.StackedEnsemble.name();

        static abstract class StackedEnsembleModelStep extends ModelingStep.ModelStep<StackedEnsembleModel> {
            
            protected final Metalearner.Algorithm _metalearnerAlgo;

            StackedEnsembleModelStep(String id, Metalearner.Algorithm algo, int priorityGroup, int weight, AutoML autoML) {
                super(NAME, Algo.StackedEnsemble, id, priorityGroup, weight, autoML);
                _metalearnerAlgo = algo;
                _ignoredConstraints = new AutoML.Constraint[] {
                        AutoML.Constraint.MODEL_COUNT,    // do not include SEs in model count (current contract: max_models = max_base_models).
                        AutoML.Constraint.FAILURE_COUNT   // do not increment failures on SEs (several issues can occur with SEs during reruns, we should still add the error to event log, but not fail AutoML).
                };
            }

            @Override
            protected void setCrossValidationParams(Model.Parameters params) {
                //added in the stack: we could probably move this here.
            }

            @Override
            protected void setWeightingParams(Model.Parameters params) {
                //Disabled: StackedEnsemble doesn't support weights in score0? 
            }

            @Override
            protected void setClassBalancingParams(Model.Parameters params) {
                //Disabled
            }

            @Override
            protected Model.Parameters applyPipeline(Key resultKey, Model.Parameters params, Map<String, Object[]> hyperParams) {
                return params; // no pipeline in SE, base models handle the transformations when making predictions.
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean canRun() {
                Key<Model>[] keys = getBaseModels();
                Work seWork = getAllocatedWork();
                if (!super.canRun()) {
                    aml().job().update(0, "Skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, String.format("Skipping StackedEnsemble '%s' due to the exclude_algos option or it is already trained.", _id));
                    return false;
                } else if (keys.length == 0) {
                    aml().job().update(seWork.consume(), "No base models; skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, String.format("No base models, due to timeouts or the exclude_algos option. Skipping StackedEnsemble '%s'.", _id));
                    return false;
                } else if (keys.length == 1) {
                    aml().job().update(seWork.consume(), "Only one base model; skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, String.format("Skipping StackedEnsemble '%s' since there is only one model to stack", _id));
                    return false;
                } else if (!isCVEnabled() && aml().getBlendingFrame() == null) {
                    aml().job().update(seWork.consume(), "Cross-validation disabled by the user and no blending frame provided; Skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, String.format("Cross-validation is disabled by the user and no blending frame was provided; skipping StackedEnsemble '%s'.", _id));
                    return false;
                }
                return !hasDoppelganger(keys);
            }
            
            @SuppressWarnings("unchecked")
            protected boolean hasDoppelganger(Key<Model>[] baseModelsKeys) {
                Key<StackedEnsembleModel>[] seModels = Arrays
                        .stream(getTrainedModelsKeys())
                        .filter(k -> isStackedEnsemble(k))
                        .toArray(Key[]::new);

                Set<Key> keySet = new HashSet<>(Arrays.asList(baseModelsKeys));
                for (Key<StackedEnsembleModel> seKey: seModels) {
                    StackedEnsembleModelStep seStep = (StackedEnsembleModelStep)aml().session().getModelingStep(seKey);
                    if (seStep._metalearnerAlgo != _metalearnerAlgo) continue;
                    
                    final StackedEnsembleParameters seParams = seKey.get()._parms;
                    final Key[] seBaseModels = seParams._base_models;
                    if (seBaseModels.length != baseModelsKeys.length) continue;
                    if (keySet.equals(new HashSet<>(Arrays.asList(seBaseModels))))
                        return true; // We already have a SE with the same base models
                }

                return false;
            }

            protected abstract Key<Model>[] getBaseModels();

            protected String getModelType(Key<Model> key) {
              ModelingStep step = aml().session().getModelingStep(key);
//              if (step != null) {  // fixme: commenting out this for now, as it interprets XRT as a DRF (which it is) and breaks legacy tests. We might want to reconsider this distinction as XRT is often very similar to DRF and doesn't bring much diversity to SEs, and the best_of SEs currently almost always have these 2.
//                return step.getAlgo().name();
//              } else { // dirty case
                String keyStr = key.toString();
                int lookupStart = keyStr.startsWith(PIPELINE_KEY_PREFIX) ? PIPELINE_KEY_PREFIX.length() : 0;
                return keyStr.substring(lookupStart, keyStr.indexOf('_', lookupStart));
//              }
            }

            protected boolean isStackedEnsemble(Key<Model> key) {
                return Algo.StackedEnsemble.name().equals(getModelType(key));
            }

            @Override
            public StackedEnsembleParameters prepareModelParameters() {
                StackedEnsembleParameters params = new StackedEnsembleParameters();
                params._valid = (aml().getValidationFrame() == null ? null : aml().getValidationFrame()._key);
                params._blending = (aml().getBlendingFrame() == null ? null : aml().getBlendingFrame()._key);
                params._keep_levelone_frame = true; //TODO Why is this true? Can be optionally turned off
                return params;
            }
            
            protected void setMetalearnerParameters(StackedEnsembleParameters params) {
                AutoMLBuildSpec buildSpec = aml().getBuildSpec();
                params._metalearner_fold_column = buildSpec.input_spec.fold_column;
                params._metalearner_nfolds = buildSpec.build_control.nfolds;
                params.initMetalearnerParams(_metalearnerAlgo);
                params._metalearner_parameters._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
                params._metalearner_parameters._keep_cross_validation_predictions = buildSpec.build_control.keep_cross_validation_predictions;
            }

            Job<StackedEnsembleModel> stack(String modelName, Key<Model>[] baseModels, boolean isLast) {
                StackedEnsembleParameters params = prepareModelParameters();
                params._base_models = baseModels;
                params._keep_base_model_predictions = !isLast; //avoids recomputing some base predictions for each SE
                
                setMetalearnerParameters(params);
                if (_metalearnerAlgo == Metalearner.Algorithm.AUTO) setAutoMetalearnerSEParameters(params);
                
                return stack(modelName, params);
            }
            
            Job<StackedEnsembleModel> stack(String modelName, StackedEnsembleParameters stackedEnsembleParameters) {
                Key<StackedEnsembleModel> modelKey = makeKey(modelName, true);
                return trainModel(modelKey, stackedEnsembleParameters);
            }

            protected void setAutoMetalearnerSEParameters(StackedEnsembleParameters stackedEnsembleParameters) {
                // add custom alpha in GLM metalearner
                GLMModel.GLMParameters metalearnerParams = (GLMModel.GLMParameters)stackedEnsembleParameters._metalearner_parameters;
                metalearnerParams._alpha = new double[]{0.5, 1.0};

                if (aml().getResponseColumn().isCategorical()) {
                    // Add logit transform
                    stackedEnsembleParameters._metalearner_transform = StackedEnsembleParameters.MetalearnerTransform.Logit;
                }
            } 

        }
        
        static class BestOfFamilySEModelStep extends StackedEnsembleModelStep {

            public BestOfFamilySEModelStep(String id, int priorityGroup, AutoML autoML) {
                this(id, Metalearner.Algorithm.AUTO, priorityGroup, autoML);
            }

            public BestOfFamilySEModelStep(String id, Metalearner.Algorithm algo, int priorityGroup, AutoML autoML) {
                this(id, algo, priorityGroup, DEFAULT_MODEL_TRAINING_WEIGHT, autoML);
            }

            public BestOfFamilySEModelStep(String id, Metalearner.Algorithm algo, int priorityGroup, int weight, AutoML autoML) {
                super((id == null ? "best_of_family_"+algo.name() : id), algo, priorityGroup, weight, autoML);
                _description = _description+" (built with "+algo.name()+" metalearner, using top model from each algorithm type)";
            }
            
            @Override
            @SuppressWarnings("unchecked")
            protected Key<Model>[] getBaseModels() {
                // Set aside List<Model> for best models per model type. Meaning best GLM, GBM, DRF, XRT, and DL (5 models).
                // This will give another ensemble that is smaller than the original which takes all models into consideration.
                List<Key<Model>> bestModelsOfEachType = new ArrayList<>();
                Set<String> typesOfGatheredModels = new HashSet<>();

                for (Key<Model> key : getTrainedModelsKeys()) {
                    // trained models are sorted (taken from leaderboard), so we only need to pick the first of each type (excluding other StackedEnsembles)
                    String type = getModelType(key);
                    if (isStackedEnsemble(key) || typesOfGatheredModels.contains(type)) continue;
                    typesOfGatheredModels.add(type);
                    bestModelsOfEachType.add(key);
                }
                return bestModelsOfEachType.toArray(new Key[0]);
            }

            @Override
            protected Job<StackedEnsembleModel> startJob() {
                return stack(_provider+"_BestOfFamily", getBaseModels(), false);
            }
        }

        static class BestNModelsSEModelStep extends StackedEnsembleModelStep {

            private final int _N;
            
            public BestNModelsSEModelStep(String id, int N, int priorityGroup, AutoML autoML) {
                this(id, Metalearner.Algorithm.AUTO, N, priorityGroup, DEFAULT_MODEL_TRAINING_WEIGHT, autoML);
            }

            public BestNModelsSEModelStep(String id, Metalearner.Algorithm algo, int N, int priorityGroup, int weight, AutoML autoML) {
                super((id == null ? "best_"+N+"_"+algo.name() : id), algo, priorityGroup, weight, autoML);
                _N = N;
                _description = _description+" (built with "+algo.name()+" metalearner, using best "+N+" non-SE models)";
            }
            
            @Override
            @SuppressWarnings("unchecked")
            protected Key<Model>[] getBaseModels() {
                return Stream.of(getTrainedModelsKeys())
                        .filter(k -> !isStackedEnsemble(k))
                        .limit(_N)
                        .toArray(Key[]::new);
            }

            @Override
            protected Job<StackedEnsembleModel> startJob() {
                return stack(_provider+"_Best"+_N, getBaseModels(), false);
            }
        }
        
        static class AllSEModelStep extends StackedEnsembleModelStep {
            public AllSEModelStep(String id, int priorityGroup, AutoML autoML) {
                this(id, Metalearner.Algorithm.AUTO, priorityGroup, autoML);
            }

            public AllSEModelStep(String id, Metalearner.Algorithm algo, int priorityGroup, AutoML autoML) {
                this(id, algo, priorityGroup, DEFAULT_MODEL_TRAINING_WEIGHT, autoML);
            }
            
            public AllSEModelStep(String id, Metalearner.Algorithm algo, int priorityGroup, int weight, AutoML autoML) {
                super((id == null ? "all_"+algo.name() : id), algo, priorityGroup, weight, autoML);
                _description = _description+" (built with "+algo.name()+" metalearner, using all AutoML models)";
            }
            
            @Override
            @SuppressWarnings("unchecked")
            protected Key<Model>[] getBaseModels() {
                return Stream.of(getTrainedModelsKeys())
                        .filter(k -> !isStackedEnsemble(k))
                        .toArray(Key[]::new);
            }

            @Override
            protected Job<StackedEnsembleModel> startJob() {
                return stack(_provider+"_AllModels", getBaseModels(), false);
            }
        }
        
        static class MonotonicSEModelStep extends StackedEnsembleModelStep {

            public MonotonicSEModelStep(String id, int priorityGroup, AutoML autoML) {
                this(id, Metalearner.Algorithm.AUTO, priorityGroup, DEFAULT_MODEL_TRAINING_WEIGHT, autoML);
            }
            
            public MonotonicSEModelStep(String id, Metalearner.Algorithm algo, int priorityGroup, int weight, AutoML autoML) {
                super((id == null ? "monotonic" : id), algo, priorityGroup, weight, autoML);
                _description = _description+" (built with "+algo.name()+" metalearner, using monotonically constrained AutoML models)";
            }
            
            boolean hasMonotoneConstrains(Key<Model> modelKey) {
                Model model = DKV.getGet(modelKey);
                try {
                    KeyValue[] mc = (KeyValue[]) PojoUtils.getFieldValue(
                            model._parms, "_monotone_constraints",
                            PojoUtils.FieldNaming.CONSISTENT);
                    return mc != null && mc.length > 0;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }

            @Override
            public boolean canRun() {
                boolean canRun = super.canRun();
                if (!canRun) return false;
                int monotoneModels=0;
                for (Key<Model> modelKey: getTrainedModelsKeys()) {
                    if (hasMonotoneConstrains(modelKey))
                        monotoneModels++;
                    if (monotoneModels >= 2)
                        return true;
                }
                if (monotoneModels == 1) {
                    aml().job().update(getAllocatedWork().consume(),
                            "Only one monotonic base model; skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining,
                            String.format("Skipping StackedEnsemble '%s' since there is only one monotonic model to stack", _id));
                } else {
                    aml().job().update(getAllocatedWork().consume(),
                            "No monotonic base model; skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining,
                            String.format("Skipping StackedEnsemble '%s' since there is no monotonic model to stack", _id));
                }
                return false;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected Key<Model>[] getBaseModels() {
                return Stream.of(getTrainedModelsKeys())
                        .filter(k -> !isStackedEnsemble(k) && hasMonotoneConstrains(k))
                        .toArray(Key[]::new);
            }

            @Override
            protected Job<StackedEnsembleModel> startJob() {
                return stack(_provider + "_Monotonic", getBaseModels(), false);
            }
        }
        
        private final ModelingStep[] defaults;
        private final ModelingStep[] optionals;

        {
            // we're going to cheat a bit: ModelingSteps needs to instantiated by the AutoML instance 
            // to convert each StepDefinition into one or more ModelingStep(s)
            // so at that time, we have access to the entire modeling plan
            // and we can dynamically generate the modeling steps that we're going to need.
            StepDefinition[] modelingPlan = aml().getBuildSpec().build_models.modeling_plan;
            if (Stream.of(modelingPlan).noneMatch(sd -> sd.getName().equals(NAME))) {
                defaults = new ModelingStep[0];
                optionals = new ModelingStep[0];
            } else {
                List<StackedEnsembleModelStep> defaultSeSteps = new ArrayList<>();
                // starting to generate the SE for each "base" group
                // ie for each group with algo steps.
                Set<String> defaultAlgoProviders = Stream.of(Algo.values())
                        .filter(a -> a != Algo.StackedEnsemble)
                        .map(Algo::name)
                        .collect(Collectors.toSet());
                int[] baseAlgoGroups = Stream.of(modelingPlan)
                        .filter(sd -> defaultAlgoProviders.contains(sd.getName()))
                        .flatMapToInt(sd -> 
                                sd.getAlias() == StepDefinition.Alias.defaults ? IntStream.of(ModelingStep.ModelStep.DEFAULT_MODEL_GROUP)
                                : sd.getAlias() == StepDefinition.Alias.grids ? IntStream.of(ModelingStep.GridStep.DEFAULT_GRID_GROUP)
                                : sd.getAlias() == StepDefinition.Alias.all ? IntStream.of(ModelingStep.ModelStep.DEFAULT_MODEL_GROUP, ModelingStep.GridStep.DEFAULT_GRID_GROUP)
                                : sd.getSteps().stream().flatMapToInt(s -> s.getGroup() == StepDefinition.Step.DEFAULT_GROUP 
                                        ? IntStream.of(ModelingStep.ModelStep.DEFAULT_MODEL_GROUP, ModelingStep.GridStep.DEFAULT_GRID_GROUP)
                                        : IntStream.of(s.getGroup())))
                        .distinct().sorted().toArray();
                
                for (int group : baseAlgoGroups) {
                    defaultSeSteps.add(new BestOfFamilySEModelStep("best_of_family_" + group, group, aml()));
                    defaultSeSteps.add(new AllSEModelStep("all_" + group, group, aml()));  // groups <=0 are ignored.
                }
                defaults = defaultSeSteps.toArray(new ModelingStep[0]);

                // now all the additional SEs are available as optionals (usually requested by id).
                int maxBaseGroup = IntStream.of(baseAlgoGroups).max().orElse(0);
                List<StackedEnsembleModelStep> optionalSeSteps = new ArrayList<>();
                if (maxBaseGroup > 0) {
                    int optionalGroup = maxBaseGroup+1;
                    optionalSeSteps.add(new MonotonicSEModelStep("monotonic", optionalGroup, aml()));
                    optionalSeSteps.add(new BestOfFamilySEModelStep("best_of_family", optionalGroup, aml()));
                    optionalSeSteps.add(new AllSEModelStep("all", optionalGroup, aml()));
                    if (Algo.XGBoost.enabled()) {
                        optionalSeSteps.add(new BestOfFamilySEModelStep("best_of_family_xgboost", Metalearner.Algorithm.xgboost, optionalGroup, aml()));
                        optionalSeSteps.add(new AllSEModelStep("all_xgboost", Metalearner.Algorithm.xgboost, optionalGroup, aml()));
                    }
                    optionalSeSteps.add(new BestOfFamilySEModelStep("best_of_family_gbm", Metalearner.Algorithm.gbm, optionalGroup, aml()));
                    optionalSeSteps.add(new AllSEModelStep("all_gbm", Metalearner.Algorithm.gbm, optionalGroup, aml()));
                    optionalSeSteps.add(new BestOfFamilySEModelStep("best_of_family_xglm", optionalGroup, aml()) {
                        @Override
                        protected boolean hasDoppelganger(Key<Model>[] baseModelsKeys) {
                            return false;
                        }

                        @Override
                        protected void setMetalearnerParameters(StackedEnsembleParameters params) {
                            super.setMetalearnerParameters(params);
                            GLMModel.GLMParameters metalearnerParams = (GLMModel.GLMParameters) params._metalearner_parameters;
                            metalearnerParams._lambda_search = true;
                        }
                    });
                    optionalSeSteps.add(new AllSEModelStep("all_xglm", optionalGroup, aml()) {
                        @Override
                        protected boolean hasDoppelganger(Key<Model>[] baseModelsKeys) {
                            Set<String> modelTypes = new HashSet<>();
                            for (Key<Model> key : baseModelsKeys) {
                                String modelType = getModelType(key);
                                if (modelTypes.contains(modelType)) return false;
                                modelTypes.add(modelType);
                            }
                            return true;
                        }

                        @Override
                        protected void setMetalearnerParameters(StackedEnsembleParameters params) {
                            super.setMetalearnerParameters(params);
                            GLMModel.GLMParameters metalearnerParams = (GLMModel.GLMParameters) params._metalearner_parameters;
                            metalearnerParams._lambda_search = true;
                        }
                    });
//                    optionalSeSteps.add(new BestNModelsSEModelStep("best_20", 20, optionalGroup, aml()));
                    int card = aml().getResponseColumn().cardinality();
                    int maxModels = card <= 2 ? 1_000 : Math.max(100, 1_000 / (card - 1));
                    optionalSeSteps.add(new BestNModelsSEModelStep("best_N", maxModels, optionalGroup, aml()));
                }
                optionals = optionalSeSteps.toArray(new ModelingStep[0]);
            }
        }

        public StackedEnsembleSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        public String getProvider() {
            return NAME;
        }

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }

        @Override
        protected ModelingStep[] getOptionals() {
            return optionals;
        }
    }

    @Override
    public String getName() {
        return StackedEnsembleSteps.NAME;
    }

    @Override
    public StackedEnsembleSteps newInstance(AutoML aml) {
        return new StackedEnsembleSteps(aml);
    }

    @Override
    public StackedEnsembleParameters newDefaultParameters() {
        return new StackedEnsembleParameters();
    }
}

