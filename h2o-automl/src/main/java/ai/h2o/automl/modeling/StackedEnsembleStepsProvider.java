package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLogEntry;
import hex.Model;
import hex.ensemble.StackedEnsembleModel;
import hex.ensemble.StackedEnsembleModel.StackedEnsembleParameters;
import water.Job;
import water.Key;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

public class StackedEnsembleStepsProvider
        implements ModelingStepsProvider<StackedEnsembleStepsProvider.StackedEnsembleSteps>
                 , ModelParametersProvider<StackedEnsembleParameters> {

    public static class StackedEnsembleSteps extends ModelingSteps {

        static abstract class StackedEnsembleModelStep extends ModelingStep.ModelStep<StackedEnsembleModel> {

            StackedEnsembleModelStep(String id, int weight, AutoML autoML) {
                super(Algo.StackedEnsemble, id, weight, autoML);
                _ignoredConstraints = new AutoML.Constraint[] {AutoML.Constraint.TIMEOUT, AutoML.Constraint.MODEL_COUNT};
            }

            @Override
            protected boolean canRun() {
                Key<Model>[] keys = getBaseModels();
                Work seWork = getAllocatedWork();
                if (seWork == null) {
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

            Job<StackedEnsembleModel> stack(String modelName, Key<Model>[] baseModels, boolean isLast) {
                AutoMLBuildSpec buildSpec = aml().getBuildSpec();
                // Set up Stacked Ensemble
                StackedEnsembleModel.StackedEnsembleParameters stackedEnsembleParameters = new StackedEnsembleParameters();
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

                Key<StackedEnsembleModel> modelKey = makeKey(modelName, false);
                return trainModel(modelKey, stackedEnsembleParameters);
            }

        }


        private ModelingStep[] defaults = new StackedEnsembleModelStep[] {
                new StackedEnsembleModelStep("best", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
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
                        return stack(_algo+"_BestOfFamily", getBaseModels(), false);
                    }
                },
                new StackedEnsembleModelStep("all", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    { _description = _description+" (built using all AutoML models)"; }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Key<Model>[] getBaseModels() {
                        return Stream.of(getTrainedModelsKeys())
                                     .filter(k -> !isStackedEnsemble(k)).toArray(Key[]::new);
                    }

                    @Override
                    protected Job<StackedEnsembleModel> startJob() {
                        return stack(_algo+"_AllModels", getBaseModels(), true);
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

