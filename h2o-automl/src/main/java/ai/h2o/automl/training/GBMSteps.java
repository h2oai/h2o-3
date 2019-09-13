package ai.h2o.automl.training;

import ai.h2o.automl.*;
import hex.grid.Grid;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.Job;

import java.util.HashMap;
import java.util.Map;

import static ai.h2o.automl.TrainingStep.GridStep.BASE_GRID_WEIGHT;
import static ai.h2o.automl.TrainingStep.ModelStep.BASE_MODEL_WEIGHT;

public class GBMSteps extends TrainingSteps {

    public static class Provider implements TrainingStepsProvider<GBMSteps> {
        @Override
        public String getName() {
            return Algo.GBM.name();
        }

        @Override
        public Class<GBMSteps> getStepsClass() {
            return GBMSteps.class;
        }
    }

    static GBMParameters prepareModelParameters() {
        GBMParameters gbmParameters = new GBMParameters();
        gbmParameters._score_tree_interval = 5;
        gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;
        return gbmParameters;
    }

    static abstract class GBMModelStep extends TrainingStep.ModelStep<GBMModel> {

        GBMModelStep(String id, int weight, AutoML autoML) {
            super(Algo.GBM, id, weight, autoML);
        }

        GBMParameters prepareModelParameters() {
            GBMParameters gbmParameters = GBMSteps.prepareModelParameters();
            gbmParameters._ntrees = 1000;
            gbmParameters._sample_rate = 0.8;
            gbmParameters._col_sample_rate = 0.8;
            gbmParameters._col_sample_rate_per_tree = 0.8;
            return gbmParameters;
        }
    }

    static abstract class GBMGridStep extends TrainingStep.GridStep<GBMModel> {
        public GBMGridStep(String id, int weight, AutoML autoML) {
            super(Algo.GBM, id, weight, autoML);
        }

        GBMParameters prepareModelParameters() {
            return GBMSteps.prepareModelParameters();
        }
    }



    private TrainingStep[] defaults = new GBMModelStep[] {
            new GBMModelStep("def_1", BASE_MODEL_WEIGHT, aml()) {
                @Override
                protected Job<GBMModel> makeJob() {
                    GBMParameters gbmParameters = prepareModelParameters();
                    gbmParameters._max_depth = 6;
                    gbmParameters._min_rows = 1;

                    return trainModel(gbmParameters);
                }
            },
            new GBMModelStep("def_2", BASE_MODEL_WEIGHT, aml()) {
                @Override
                protected Job<GBMModel> makeJob() {
                    GBMParameters gbmParameters = prepareModelParameters();
                    gbmParameters._max_depth = 7;
                    gbmParameters._min_rows = 10;

                    return trainModel(gbmParameters);
                }
            },
            new GBMModelStep("def_3", BASE_MODEL_WEIGHT,aml()) {
                @Override
                protected Job<GBMModel> makeJob() {
                    GBMParameters gbmParameters = prepareModelParameters();
                    gbmParameters._max_depth = 8;
                    gbmParameters._min_rows = 10;

                    return trainModel(gbmParameters);
                }
            },
            new GBMModelStep("def_4", BASE_MODEL_WEIGHT, aml()) {
                @Override
                protected Job<GBMModel> makeJob() {
                    GBMParameters gbmParameters = prepareModelParameters();
                    gbmParameters._max_depth = 10;
                    gbmParameters._min_rows = 10;

                    return trainModel(gbmParameters);
                }
            },
            new GBMModelStep("def_5", BASE_MODEL_WEIGHT, aml()) {
                @Override
                protected Job<GBMModel> makeJob() {
                    GBMParameters gbmParameters = prepareModelParameters();
                    gbmParameters._max_depth = 15;
                    gbmParameters._min_rows = 100;

                    return trainModel(gbmParameters);
                }
            },
    };

    private TrainingStep[] grids = new GBMGridStep[] {
            new GBMGridStep("grid_1", 3*BASE_GRID_WEIGHT, aml()) {
                @Override
                protected Job<Grid> makeJob() {
                    GBMParameters gbmParameters = prepareModelParameters();

                    Map<String, Object[]> searchParams = new HashMap<>();
                    searchParams.put("_ntrees", new Integer[]{10000});
                    searchParams.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
                    searchParams.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
                    searchParams.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
                    searchParams.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00});
                    searchParams.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0});
                    searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0});
                    searchParams.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

                    return hyperparameterSearch(gbmParameters, searchParams);
                }
            },
    };

    public GBMSteps(AutoML autoML) {
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
