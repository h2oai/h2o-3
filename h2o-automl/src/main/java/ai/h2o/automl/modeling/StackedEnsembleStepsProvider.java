package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.WorkAllocations.Work;
import ai.h2o.automl.events.EventLogEntry;
import ai.h2o.automl.preprocessing.PreprocessingConfig;
import ai.h2o.automl.preprocessing.TargetEncoding;
import hex.KeyValue;
import hex.Model;
import hex.ensemble.Metalearner;
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
            
            protected final Metalearner.Algorithm _metalearnerAlgo;

            StackedEnsembleModelStep(String id, Metalearner.Algorithm algo, int weight, int priorityGroup, AutoML autoML) {
                super(Algo.StackedEnsemble, id, weight, priorityGroup,autoML);
                _metalearnerAlgo = algo;
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
            @SuppressWarnings("unchecked")
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

                Set<Key> keySet = new HashSet<>(Arrays.asList(keys));
                for (Key<StackedEnsembleModel> seKey: seModels) {
                    final Key[] baseModels = seKey.get()._parms._base_models;
                    if (baseModels.length != keys.length) continue;
                    if (keySet.equals(new HashSet<>(Arrays.asList(baseModels))))
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

            @Override
            protected StackedEnsembleParameters prepareModelParameters() {
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

            protected StackedEnsembleParameters extendSEParameters(StackedEnsembleParameters stackedEnsembleParameters) {
                return stackedEnsembleParameters;
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
                this((id == null ? "best_of_family" : id), Metalearner.Algorithm.AUTO, DEFAULT_MODEL_TRAINING_WEIGHT/2,  priorityGroup, autoML);
            }

            public BestOfFamilySEModelStep(String id, Metalearner.Algorithm algo, int weight, int priorityGroup, AutoML autoML) {
                super(id, algo, weight, priorityGroup, autoML);
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
                return stack(_algo+"_BestOfFamily", getBaseModels(), false);
            }
        }

        static class BestNModelsSEModelStep extends StackedEnsembleModelStep {

            private final int _N;
            
            public BestNModelsSEModelStep(String id, int N, int priorityGroup, AutoML autoML) {
                this((id == null ? "best"+N : id), Metalearner.Algorithm.AUTO, N, DEFAULT_MODEL_TRAINING_WEIGHT, priorityGroup, autoML);
            }

            public BestNModelsSEModelStep(String id, Metalearner.Algorithm algo, int N, int weight, int priorityGroup, AutoML autoML) {
                super(id, algo, weight, priorityGroup, autoML);
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
                return stack(_algo+"_Best"+_N, getBaseModels(), false);
            }
        }
        
        static class AllSEModelStep extends StackedEnsembleModelStep {
            public AllSEModelStep(String id, int priorityGroup, AutoML autoML) {
                this((id == null ? "all" : id), Metalearner.Algorithm.AUTO, DEFAULT_MODEL_TRAINING_WEIGHT, priorityGroup, autoML);
            }
            
            public AllSEModelStep(String id, Metalearner.Algorithm algo, int weight, int priorityGroup, AutoML autoML) {
                super(id, algo, weight, priorityGroup, autoML);
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
                return stack(_algo+"_AllModels", getBaseModels(), false);
            }
        }
        
        static class MonotonicSEModelStep extends StackedEnsembleModelStep {

            public MonotonicSEModelStep(String id, int priorityGroup, AutoML autoML) {
                this((id == null ? "monotonic" : id), Metalearner.Algorithm.AUTO, DEFAULT_MODEL_TRAINING_WEIGHT, priorityGroup, autoML);
            }
            
            public MonotonicSEModelStep(String id, Metalearner.Algorithm algo, int weight, int priorityGroup, AutoML autoML) {
                super(id, algo, weight, priorityGroup, autoML);
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
                return stack(_algo + "_Monotonic", getBaseModels(), false);
            }
        }
        
        private final ModelingStep[] defaults;

        {
            int lastGroup = 5;
            List<StackedEnsembleModelStep> seSteps = new ArrayList<>();
            for (int group = 1; group <= lastGroup; group++) {
                seSteps.add(new BestOfFamilySEModelStep("best_of_family_"+group, group, aml()));
                // small optimization for the important first group: 
                // we know that Best=All, so we don't want to count an additional SE in the time budget knowing in advance that it won't be used.
                if (group > 1) seSteps.add(new AllSEModelStep("all_"+group, group, aml()));
            }
            seSteps.add(new MonotonicSEModelStep(null, lastGroup+1, aml()));
            seSteps.add(new BestOfFamilySEModelStep("best_of_family_xgboost", Metalearner.Algorithm.xgboost,
                    DEFAULT_MODEL_TRAINING_WEIGHT, lastGroup+1, aml()));
            seSteps.add(new AllSEModelStep("all_xgboost", Metalearner.Algorithm.xgboost,
                    DEFAULT_MODEL_TRAINING_WEIGHT, lastGroup+2, aml()));
            seSteps.add(new BestOfFamilySEModelStep("best_of_family_gbm", Metalearner.Algorithm.gbm,
                    DEFAULT_MODEL_TRAINING_WEIGHT, lastGroup+1, aml()));
            seSteps.add(new AllSEModelStep("all_gbm", Metalearner.Algorithm.gbm,
                    DEFAULT_MODEL_TRAINING_WEIGHT, lastGroup+2, aml()));
            seSteps.add(new BestOfFamilySEModelStep("best_of_family_ext", lastGroup+3, aml()) {
                @Override
                protected void setMetalearnerParameters(StackedEnsembleParameters params) {
                    super.setMetalearnerParameters(params);
                    GLMModel.GLMParameters metalearnerParams = (GLMModel.GLMParameters)params._metalearner_parameters;
                    metalearnerParams._lambda_search = true;
                }
            });
            seSteps.add(new AllSEModelStep("all_ext", lastGroup+3, aml()) {
                @Override
                protected void setMetalearnerParameters(StackedEnsembleParameters params) {
                    super.setMetalearnerParameters(params);
                    GLMModel.GLMParameters metalearnerParams = (GLMModel.GLMParameters)params._metalearner_parameters;
                    metalearnerParams._lambda_search = true;
                }
            });
            seSteps.add(new BestNModelsSEModelStep(null, 20, lastGroup+4, aml()));
            lastGroup=100;
            seSteps.add(new BestOfFamilySEModelStep("best_of_family_"+lastGroup, lastGroup, aml()));
            seSteps.add(new BestNModelsSEModelStep(null, 100, lastGroup, aml()));
            defaults = seSteps.toArray(new ModelingStep[0]);
        }

        public StackedEnsembleSteps(AutoML autoML) {
            super(autoML);
        }

        @Override
        protected ModelingStep[] getDefaultModels() {
            return defaults;
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

