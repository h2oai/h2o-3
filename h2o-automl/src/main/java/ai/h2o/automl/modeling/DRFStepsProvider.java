package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import hex.tree.SharedTreeModel.SharedTreeParameters.HistogramType;
import hex.tree.drf.DRFModel;
import hex.tree.drf.DRFModel.DRFParameters;
import water.Job;
import water.Key;

import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

public class DRFStepsProvider
        implements ModelingStepsProvider<DRFStepsProvider.DRFSteps>
                 , ModelParametersProvider<DRFParameters> {

    public static class DRFSteps extends ModelingSteps {

        static abstract class DRFModelStep extends ModelingStep.ModelStep<DRFModel> {

            DRFModelStep(String id, int weight, AutoML autoML) {
                super(Algo.DRF, id, weight, autoML);
            }

            DRFParameters prepareModelParameters() {
                DRFParameters drfParameters = new DRFParameters();
                drfParameters._score_tree_interval = 5;
                return drfParameters;
            }
        }

        static abstract class DRFDummyStep extends ModelingStep.DummyStep<DRFModel> {
            DRFDummyStep(String id, int weight, AutoML autoML){
                super(Algo.DRF, id, weight, autoML);
            }
        }

        private ModelingStep[] defaults = new DRFModelStep[] {
                new DRFModelStep("def_1", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<DRFModel> startJob() {
                        DRFParameters drfParameters = prepareModelParameters();
                        return trainModel(drfParameters);
                    }
                },
                new DRFModelStep("XRT", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    { _description = _description+" (Extremely Randomized Trees)"; }

                    @Override
                    protected Job<DRFModel> startJob() {
                        DRFParameters drfParameters = prepareModelParameters();
                        drfParameters._histogram_type = HistogramType.Random;

                        Key<DRFModel> key = makeKey("XRT", true);
                        return trainModel(key, drfParameters);
                    }
                },
        };

        private ModelingStep[] grids = new ModelingStep[0];

        private ModelingStep[] dummies = new ModelingStep[]{
                new DRFDummyStep("dummy_sleep", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {}
        };

        public DRFSteps(AutoML autoML) {
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

        @Override
        protected ModelingStep[] getDummySteps() {
            return dummies;
        }
    }

    @Override
    public String getName() {
        return Algo.DRF.name();
    }

    @Override
    public DRFSteps newInstance(AutoML aml) {
        return new DRFSteps(aml);
    }

    @Override
    public DRFParameters newDefaultParameters() {
        return new DRFParameters();
    }
}

