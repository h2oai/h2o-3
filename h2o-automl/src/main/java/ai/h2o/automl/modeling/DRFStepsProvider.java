package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import hex.tree.SharedTreeModel.SharedTreeParameters.HistogramType;
import hex.tree.drf.DRFModel;
import hex.tree.drf.DRFModel.DRFParameters;
import water.Job;
import water.Key;

public class DRFStepsProvider
        implements ModelingStepsProvider<DRFStepsProvider.DRFSteps>
                 , ModelParametersProvider<DRFParameters> {

    public static class DRFSteps extends ModelingSteps {

        static final String NAME = Algo.DRF.name();
        
        static abstract class DRFModelStep extends ModelingStep.ModelStep<DRFModel> {

            DRFModelStep(String id, AutoML autoML) {
                super(NAME, Algo.DRF, id, autoML);
            }

            public DRFParameters prepareModelParameters() {
                DRFParameters params = new DRFParameters();
                params._score_tree_interval = 5;
                // Disable early stopping for DRF and XRT since we only have 50 trees by default.
                // With only 50 trees and score_tree_interval=5, early stopping has very limited
                // checkpoints (10 total) to evaluate. Disabling allows the model to use all trees.
                // See PUBDEV-5884 for benchmark analysis.
                params._stopping_rounds = 0;
                return params;
            }
        }


        private final ModelingStep[] defaults = new DRFModelStep[] {
                new DRFModelStep("def_1", aml()) {},
                new DRFModelStep("XRT", aml()) {
                    { _description = _description+" (Extremely Randomized Trees)"; }

                    @Override
                    public DRFParameters prepareModelParameters() {
                        DRFParameters params = super.prepareModelParameters();
                        params._histogram_type = HistogramType.Random;
                        return params;
                    }

                    @Override
                    protected Job<DRFModel> startJob() {
                        Key<DRFModel> key = makeKey("XRT", true);
                        return trainModel(key, prepareModelParameters());
                    }
                },
        };

        public DRFSteps(AutoML autoML) {
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
    }

    @Override
    public String getName() {
        return DRFSteps.NAME;
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

