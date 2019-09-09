package ai.h2o.automl.training;

import ai.h2o.automl.*;
import hex.deeplearning.DeepLearningModel;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import water.Job;

import java.util.HashMap;
import java.util.Map;

public class DeepLearningSteps extends TrainingSteps {

    public static class Provider implements TrainingStepsProvider<DeepLearningSteps> {
        @Override
        public String getName() {
            return Algo.DeepLearning.name();
        }

        @Override
        public Class<DeepLearningSteps> getStepsClass() {
            return DeepLearningSteps.class;
        }
    }

    static abstract class DeepLearningModelStep extends TrainingStep.ModelStep<DeepLearningModel> {
        public DeepLearningModelStep(String id, AutoML autoML) {
            super(Algo.DeepLearning, id, autoML);
        }
    }

    static abstract class DeepLearningGridStep extends TrainingStep.GridStep<DeepLearningModel> {

        DeepLearningGridStep(String id, AutoML autoML) {
            super(Algo.DeepLearning, id, autoML);
        }

        DeepLearningParameters prepareModelParameters() {
            DeepLearningParameters dlParameters = new DeepLearningParameters();

            dlParameters._epochs = 10000; // early stopping takes care of epochs - no need to tune!
            dlParameters._adaptive_rate = true;
            dlParameters._activation = DeepLearningParameters.Activation.RectifierWithDropout;

            return dlParameters;
        }


        Map<String, Object[]> prepareSearchParams() {
            Map<String, Object[]> searchParams = new HashMap<>();

            searchParams.put("_rho", new Double[] { 0.9, 0.95, 0.99 });
            searchParams.put("_epsilon", new Double[] { 1e-6, 1e-7, 1e-8, 1e-9 });
            searchParams.put("_input_dropout_ratio", new Double[] { 0.0, 0.05, 0.1, 0.15, 0.2 });

            return searchParams;
        }
    }


    private TrainingStep[] defaults = new DeepLearningModelStep[] {
            new DeepLearningModelStep("def_1", _aml) {
                @Override
                protected Job makeJob() {
                    DeepLearningParameters dlParameters = new DeepLearningParameters();  // don't use common params for default DL
                    dlParameters._hidden = new int[]{ 10, 10, 10 };
                    return trainModel(dlParameters);
                }
            },
    };

    private TrainingStep[] grids = new DeepLearningGridStep[] {
            new DeepLearningGridStep("grid_1", _aml) {
                @Override
                protected Job makeJob() {
                    DeepLearningParameters dlParameters = prepareModelParameters();

                    Map<String, Object[]> searchParams = prepareSearchParams();
                    searchParams.put("_hidden", new Integer[][] {
                            {50},
                            {200},
                            {500}
                    });
                    searchParams.put("_hidden_dropout_ratios", new Double[][] {
                            { 0.0 },
                            { 0.1 },
                            { 0.2 },
                            { 0.3 },
                            { 0.4 },
                            { 0.5 }
                    });

                    return hyperparameterSearch(dlParameters, searchParams);
                }
            },
            new DeepLearningGridStep("grid_2", _aml) {
                @Override
                protected Job makeJob() {
                    DeepLearningParameters dlParameters = prepareModelParameters();

                    Map<String, Object[]> searchParams = prepareSearchParams();
                    searchParams.put("_hidden", new Integer[][] {
                            {50, 50},
                            {200, 200},
                            {500, 500}
                    });
                    searchParams.put("_hidden_dropout_ratios", new Double[][] {
                            { 0.0, 0.0 },
                            { 0.1, 0.1 },
                            { 0.2, 0.2 },
                            { 0.3, 0.3 },
                            { 0.4, 0.4 },
                            { 0.5, 0.5 }
                    });
                    return hyperparameterSearch(dlParameters, searchParams);
                }
            },
            new DeepLearningGridStep("grid_3", _aml) {
                @Override
                protected Job makeJob() {
                    DeepLearningParameters dlParameters = prepareModelParameters();

                    Map<String, Object[]> searchParams = prepareSearchParams();
                    searchParams.put("_hidden", new Integer[][] {
                            {50, 50, 50},
                            {200, 200, 200},
                            {500, 500, 500}
                    });
                    searchParams.put("_hidden_dropout_ratios", new Double[][] {
                            { 0.0, 0.0, 0.0 },
                            { 0.1, 0.1, 0.1 },
                            { 0.2, 0.2, 0.2 },
                            { 0.3, 0.3, 0.3 },
                            { 0.4, 0.4, 0.4 },
                            { 0.5, 0.5, 0.5 }
                    });

                    return hyperparameterSearch(dlParameters, searchParams);
                }
            },
    };

    public DeepLearningSteps(AutoML autoML) {
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
