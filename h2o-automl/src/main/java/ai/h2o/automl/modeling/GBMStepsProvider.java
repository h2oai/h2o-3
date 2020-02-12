package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import hex.Model;
import hex.grid.Grid;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.Job;

import java.util.HashMap;
import java.util.Map;

import static ai.h2o.automl.ModelingStep.GridStep.DEFAULT_GRID_TRAINING_WEIGHT;
import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

public class GBMStepsProvider
        implements ModelingStepsProvider<GBMStepsProvider.GBMSteps>
                 , ModelParametersProvider<GBMParameters> {

    public static class GBMSteps extends ModelingSteps {

        static GBMParameters prepareModelParameters() {
            GBMParameters gbmParameters = new GBMParameters();
            gbmParameters._learn_rate = 0.5;  //default is 0.1
            gbmParameters._score_tree_interval = 5;
            gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;
            return gbmParameters;
        }

        static abstract class GBMModelStep extends ModelingStep.ModelStep<GBMModel> {

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

        static abstract class GBMGridStep extends ModelingStep.GridStep<GBMModel> {
            public GBMGridStep(String id, int weight, AutoML autoML) {
                super(Algo.GBM, id, weight, autoML);
            }

            GBMParameters prepareModelParameters() {
                GBMParameters gbmParameters = GBMSteps.prepareModelParameters();
                gbmParameters._ntrees = 10000;
                return gbmParameters;
            }
        }


        private ModelingStep[] defaults = new GBMModelStep[] {
                new GBMModelStep("def_1", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<GBMModel> startJob() {
                        GBMParameters gbmParameters = prepareModelParameters();
                        gbmParameters._max_depth = 6;
                        gbmParameters._min_rows = 1;

                        return trainModel(gbmParameters);
                    }
                },
                new GBMModelStep("def_2", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<GBMModel> startJob() {
                        GBMParameters gbmParameters = prepareModelParameters();
                        gbmParameters._max_depth = 7;
                        gbmParameters._min_rows = 10;

                        return trainModel(gbmParameters);
                    }
                },
                new GBMModelStep("def_3", DEFAULT_MODEL_TRAINING_WEIGHT,aml()) {
                    @Override
                    protected Job<GBMModel> startJob() {
                        GBMParameters gbmParameters = prepareModelParameters();
                        gbmParameters._max_depth = 8;
                        gbmParameters._min_rows = 10;

                        return trainModel(gbmParameters);
                    }
                },
                new GBMModelStep("def_4", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<GBMModel> startJob() {
                        GBMParameters gbmParameters = prepareModelParameters();
                        gbmParameters._max_depth = 10;
                        gbmParameters._min_rows = 10;

                        return trainModel(gbmParameters);
                    }
                },
                new GBMModelStep("def_5", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<GBMModel> startJob() {
                        GBMParameters gbmParameters = prepareModelParameters();
                        gbmParameters._max_depth = 15;
                        gbmParameters._min_rows = 100;

                        return trainModel(gbmParameters);
                    }
                },
        };

        private ModelingStep[] grids = new GBMGridStep[] {
                new GBMGridStep("grid_1", 3* DEFAULT_GRID_TRAINING_WEIGHT, aml()) {
                    @Override
                    protected Job<Grid> startJob() {
                        GBMParameters gbmParameters = prepareModelParameters();

                        Map<String, Object[]> searchParams = new HashMap<>();
                        searchParams.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
                        searchParams.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
//                        searchParams.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
                        searchParams.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00});
                        searchParams.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0});
                        searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0});
                        searchParams.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

                        return hyperparameterSearch(gbmParameters, searchParams);
                    }
                },
        };

        private ModelingStep[] exploitation = new GBMModelStep[] {
               new GBMModelStep("exploit_1", DEFAULT_MODEL_TRAINING_WEIGHT, aml()) {

                   private GBMModel getBestGBM() {
                     for (Model model : getTrainedModels()) {
                         if (model instanceof GBMModel) {
                             return (GBMModel) model;
                         }
                     }
                     return null;
                   }

                   @Override
                   protected boolean canRun() {
                       // TODO: add event log message here?
                       return getBestGBM() != null;
                   }

                   @Override
                   protected Job<GBMModel> startJob() {
                       GBMModel bestGBM = getBestGBM();
                       GBMParameters gbmParameters = (GBMParameters) bestGBM._parms.clone();
                       gbmParameters._learn_rate = 0.5;
                       gbmParameters._learn_rate_annealing = 0.9;
                       return trainModel(gbmParameters);
                   }
               }
        };

        public GBMSteps(AutoML autoML) {
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
        protected ModelingStep[] getExploitation() {
            return exploitation;
        }
    }

    @Override
    public String getName() {
        return Algo.GBM.name();
    }

    @Override
    public GBMSteps newInstance(AutoML aml) {
        return new GBMSteps(aml);
    }

    @Override
    public GBMParameters newDefaultParameters() {
        return new GBMParameters();
    }
}

