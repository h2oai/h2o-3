package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLogEntry;
import ai.h2o.automl.preprocessing.PreprocessingConfig;
import ai.h2o.automl.preprocessing.PreprocessingStepDefinition;
import ai.h2o.automl.preprocessing.TargetEncoding;
import hex.KeyValue;
import hex.Model;
import hex.ensemble.StackedEnsembleModel;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import hex.glm.GLMModel;
import water.DKV;
import water.Job;
import water.Key;
import water.util.ArrayUtils;
import water.util.PojoUtils;

import java.util.*;
import java.util.stream.Stream;

import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

public class StackedEnsembleStepsProvider
        implements ModelingStepsProvider<StackedEnsembleStepsProvider.StackedEnsembleSteps>
                 , ModelParametersProvider<StackedEnsembleParameters> {

    public static class StackedEnsembleSteps extends ModelingSteps {

        static abstract class StackedEnsembleModelStep extends ModelingStep.ModelStep<StackedEnsembleModel> {

            StackedEnsembleModelStep(String id, int weight, int priorityGroup, AutoML autoML) {
                super(Algo.StackedEnsemble, id, weight, priorityGroup,autoML);
                _ignoredConstraints = new AutoML.Constraint[] {AutoML.Constraint.MODEL_COUNT};
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
            protected PreprocessingConfig getPreprocessingConfig() {
                //SE should not have TE applied, the base models already do it.
                PreprocessingConfig config = super.getPreprocessingConfig();
                config.put(TargetEncoding.CONFIG_ENABLED, false);
                return config;
            }

            @Override
            protected boolean canRun() {
                Key<Model>[] keys = getBaseModels();
                Work seWork = getAllocatedWork();
                if (!super.canRun()) {
                    aml().job().update(0, "Skipping this StackedEnsemble");
                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, String.format("Skipping StackedEnsemble '%s' due to the exclude_algos option.", _id));
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

                Key<StackedEnsembleModel>[] seModels = Arrays
                        .stream(getTrainedModelsKeys())
                        .filter(k -> isStackedEnsemble(k))
                        .toArray(Key[]::new);

                for (Key<StackedEnsembleModel> seKey: seModels) {
                    final Key[] baseModels = seKey.get()._parms._base_models;
                    if (baseModels.length != getBaseModels().length) continue;
                    if (Arrays.stream(getBaseModels())
                            .filter(model -> ArrayUtils.contains(baseModels, model))
                            .count() == baseModels.length)
                        return false; // We already have a SE with the same base models
                }

                return true;
            }

            protected abstract Key<Model>[] getBaseModels();

            protected String getModelType(Key<Model> key) {
                String keyStr = key.toString();
                return keyStr.substring(0, keyStr.indexOf('_'));
            }

            protected boolean isStackedEnsemble(Key<Model> key) {
                return key.toString().startsWith(_algo.name());
            }

            Job<StackedEnsembleModel> stack(Key modelKey, Key<Model>[] baseModels, boolean isLast) {
                StackedEnsembleParameters stackedEnsembleParameters = getStackedEnsembleParameters(baseModels, isLast);
                return trainModel(modelKey, stackedEnsembleParameters);
            }

            protected StackedEnsembleParameters getStackedEnsembleParameters(Key<Model>[] baseModels, boolean isLast) {
                AutoMLBuildSpec buildSpec = aml().getBuildSpec();
                // Set up Stacked Ensemble
                StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleParameters();
                stackedEnsembleParameters._base_models = baseModels;
                stackedEnsembleParameters._valid = (aml().getValidationFrame() == null ? null : aml().getValidationFrame()._key);
                stackedEnsembleParameters._blending = (aml().getBlendingFrame() == null ? null : aml().getBlendingFrame()._key);
                stackedEnsembleParameters._keep_levelone_frame = true; //TODO Why is this true? Can be optionally turned off
                stackedEnsembleParameters._keep_base_model_predictions = !isLast; //avoids recomputing some base predictions for each SE
                // Add cross-validation args
                stackedEnsembleParameters._metalearner_fold_column = buildSpec.input_spec.fold_column;
                stackedEnsembleParameters._metalearner_nfolds = buildSpec.build_control.nfolds;
                stackedEnsembleParameters.initMetalearnerParams();
                stackedEnsembleParameters._metalearner_parameters._keep_cross_validation_models = buildSpec.build_control.keep_cross_validation_models;
                stackedEnsembleParameters._metalearner_parameters._keep_cross_validation_predictions = buildSpec.build_control.keep_cross_validation_predictions;
                // add custom alpha in GLM metalearner
                GLMModel.GLMParameters metalearner_params = (GLMModel.GLMParameters)stackedEnsembleParameters._metalearner_parameters;
                metalearner_params._alpha = new double[]{0.5, 1.0};

                if (aml().getResponseColumn().isCategorical()) {
                    // Add logit transform
                    stackedEnsembleParameters._metalearner_transform = StackedEnsembleParameters.MetalearnerTransform.Logit;
                }

                return stackedEnsembleParameters;
            }

        }


        private ModelingStep[] defaults = new StackedEnsembleModelStep[] {
                new StackedEnsembleModelStep("best10", DEFAULT_MODEL_TRAINING_WEIGHT, 10,aml()) {
                    { _description = _description+" (built using top model from each algorithm type)"; }

                    @Override
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
                        return stack(makeKey(_algo + "_BestOfFamily", true), getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("all10", DEFAULT_MODEL_TRAINING_WEIGHT, 10,aml()) {
                    { _description = _description+" (built using all AutoML models)"; }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Key<Model>[] getBaseModels() {
                        return Stream.of(getTrainedModelsKeys())
                                     .filter(k -> !isStackedEnsemble(k)).toArray(Key[]::new);
                    }

                    @Override
                    protected Job<StackedEnsembleModel> startJob() {
                        return stack(makeKey(_algo + "_AllModels", true), getBaseModels(), false);
                    }
                },

                new StackedEnsembleModelStep("best20", DEFAULT_MODEL_TRAINING_WEIGHT, 20,aml()) {
                    { _description = _description+" (built using top model from each algorithm type)"; }

                    @Override
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
                        return stack(makeKey(_algo + "_BestOfFamily", true), getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("all20", DEFAULT_MODEL_TRAINING_WEIGHT,20 ,aml()) {
                    { _description = _description+" (built using all AutoML models)"; }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Key<Model>[] getBaseModels() {
                        return Stream.of(getTrainedModelsKeys())
                                .filter(k -> !isStackedEnsemble(k)).toArray(Key[]::new);
                    }

                    @Override
                    protected Job<StackedEnsembleModel> startJob() {
                        return stack(makeKey(_algo + "_AllModels", true), getBaseModels(), false);
                    }
                },

                new StackedEnsembleModelStep("best30", DEFAULT_MODEL_TRAINING_WEIGHT, 30 ,aml()) {
                    { _description = _description+" (built using top model from each algorithm type)"; }

                    @Override
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
                        return stack(makeKey(_algo + "_BestOfFamily", true), getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("all30", DEFAULT_MODEL_TRAINING_WEIGHT, 30,aml()) {
                    { _description = _description+" (built using all AutoML models)"; }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Key<Model>[] getBaseModels() {
                        return Stream.of(getTrainedModelsKeys())
                                .filter(k -> !isStackedEnsemble(k)).toArray(Key[]::new);
                    }

                    @Override
                    protected Job<StackedEnsembleModel> startJob() {
                        return stack(makeKey(_algo + "_AllModels", true), getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("best90", DEFAULT_MODEL_TRAINING_WEIGHT, 90,aml()) {
                    { _description = _description+" (built using top model from each algorithm type)"; }

                    @Override
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
                        return stack(makeKey(_algo + "_BestOfFamily", true), getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("all90", DEFAULT_MODEL_TRAINING_WEIGHT, 90,aml()) {
                    { _description = _description+" (built using all AutoML models)"; }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Key<Model>[] getBaseModels() {
                        return Stream.of(getTrainedModelsKeys())
                                .filter(k -> !isStackedEnsemble(k)).toArray(Key[]::new);
                    }

                    @Override
                    protected Job<StackedEnsembleModel> startJob() {
                        return stack(makeKey(_algo + "_AllModels", true), getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("best100", DEFAULT_MODEL_TRAINING_WEIGHT, 100,aml()) {
                    { _description = _description+" (built using top model from each algorithm type)"; }

                    @Override
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
                        return stack(makeKey(_algo + "_BestOfFamily", true), getBaseModels(), false);
                    }
                },

                new StackedEnsembleModelStep("monotonic", DEFAULT_MODEL_TRAINING_WEIGHT, Integer.MAX_VALUE,aml()) {
                    { _description = _description+" (built using monotonically constrained AutoML models)"; }

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
                    protected boolean canRun() {
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
                        return stack(makeKey(_algo + "_Monotonic", false), getBaseModels(), false);
                    }
                },

                new StackedEnsembleModelStep("all100", DEFAULT_MODEL_TRAINING_WEIGHT, 100,aml()) {
                    { _description = _description+" (built using all AutoML models)"; }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Key<Model>[] getBaseModels() {
                        return Stream.of(getTrainedModelsKeys())
                                .filter(k -> !isStackedEnsemble(k)).toArray(Key[]::new);
                    }

                    @Override
                    protected Job<StackedEnsembleModel> startJob() {
                        return stack(makeKey(_algo + "_AllModels", true), getBaseModels(), true);
                    }
                },
        };

        private ModelingStep[] grids = new ModelingStep[0];

        public StackedEnsembleSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
        }

        @Override
        protected ModelingStep[] getGrids() {
            return grids;
        }
    }

    @Override
    public String getName() {
        return Algo.StackedEnsemble.name();
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

