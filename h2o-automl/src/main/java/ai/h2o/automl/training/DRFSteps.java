package ai.h2o.automl.training;

import ai.h2o.automl.*;
import hex.tree.SharedTreeModel.SharedTreeParameters.HistogramType;
import hex.tree.drf.DRFModel;
import hex.tree.drf.DRFModel.DRFParameters;
import water.Job;
import water.Key;

import static ai.h2o.automl.TrainingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

public class DRFSteps extends TrainingSteps {

    public static class Provider implements TrainingStepsProvider<DRFSteps> {
        @Override
        public String getName() {
            return Algo.DRF.name();
        }

        @Override
        public Class<DRFSteps> getStepsClass() {
            return DRFSteps.class;
        }
    }

    static abstract class DRFModelStep extends TrainingStep.ModelStep<DRFModel> {

        DRFModelStep(String id, int weight, AutoML autoML) {
            super(Algo.DRF, id, weight, autoML);
        }

        DRFParameters prepareModelParameters() {
            DRFParameters drfParameters = new DRFParameters();
            drfParameters._score_tree_interval = 5;
            return drfParameters;
        }
    }


    private TrainingStep[] defaults = new DRFModelStep[] {
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

    private TrainingStep[] grids = new TrainingStep[0];

    public DRFSteps(AutoML autoML) {
        super(autoML);
    }

    @Override
    protected TrainingStep[] getDefaultModels() {
        return defaults;
    }

    @Override
    protected TrainingStep[] getGrids() {
        return grids;
    }
}
