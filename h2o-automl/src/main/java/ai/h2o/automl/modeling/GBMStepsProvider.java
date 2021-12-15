package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.ModelSelectionStrategies.KeepBestN;
import ai.h2o.automl.events.EventLogEntry;
import hex.Model;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import hex.tree.gbm.GBMModel.GBMParameters;
import water.Job;
import water.Key;

import java.util.*;

public class GBMStepsProvider
        implements ModelingStepsProvider<GBMStepsProvider.GBMSteps>
                 , ModelParametersProvider<GBMParameters> {
    public static class GBMSteps extends ModelingSteps {

        static final String NAME = Algo.GBM.name();
        
        static GBMParameters prepareModelParameters() {
            GBMParameters params = new GBMParameters();
            params._score_tree_interval = 5;
            params._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;
            return params;
        }

        static abstract class GBMModelStep extends ModelingStep.ModelStep<GBMModel> {

            GBMModelStep(String id, AutoML autoML) {
                super(NAME, Algo.GBM, id, autoML);
            }

            public GBMParameters prepareModelParameters() {
                GBMParameters params = GBMSteps.prepareModelParameters();
                params._ntrees = 10000;
                params._sample_rate = 0.8;
                params._col_sample_rate = 0.8;
                params._col_sample_rate_per_tree = 0.8;
                setDistributionParameters(params);
                return params;
            }
        }

        static abstract class GBMGridStep extends ModelingStep.GridStep<GBMModel> {
            public GBMGridStep(String id, AutoML autoML) {
                super(NAME, Algo.GBM, id, autoML);
            }

            public GBMParameters prepareModelParameters() {
                GBMParameters params = GBMSteps.prepareModelParameters();
                params._ntrees = 10000;
                setDistributionParameters(params);
                return params;
            }
        }

        static abstract class GBMExploitationStep extends ModelingStep.SelectionStep<GBMModel> {

            protected GBMModel getBestGBM() {
                for (Model model : getTrainedModels()) {
                    if (model instanceof GBMModel) {
                        return (GBMModel) model;
                    }
                }
                return null;
            }

            @Override
            public boolean canRun() {
                return super.canRun() && getBestGBM() != null;
            }
            public GBMExploitationStep(String id, AutoML autoML) {
                super(NAME, Algo.GBM, id, autoML);
                if (autoML.getBuildSpec().build_models.exploitation_ratio > 0)
                    _ignoredConstraints = new AutoML.Constraint[] { AutoML.Constraint.MODEL_COUNT };
            }
        }



        private final ModelingStep[] defaults = new GBMModelStep[] {
                new GBMModelStep("def_1", aml()) {
                    @Override
                    public GBMParameters prepareModelParameters() {
                        GBMParameters params = super.prepareModelParameters();
                        params._max_depth = 6;
                        params._min_rows = 1;
                        return params;
                    }
                },
                new GBMModelStep("def_2", aml()) {
                    @Override
                    public GBMParameters prepareModelParameters() {
                        GBMParameters params = super.prepareModelParameters();
                        params._max_depth = 7;
                        params._min_rows = 10;
                        return params;
                    }
                },
                new GBMModelStep("def_3", aml()) {
                    @Override
                    public GBMParameters prepareModelParameters() {
                        GBMParameters params = super.prepareModelParameters();
                        params._max_depth = 8;
                        params._min_rows = 10;
                        return params;
                    }
                },
                new GBMModelStep("def_4", aml()) {
                    @Override
                    public GBMParameters prepareModelParameters() {
                        GBMParameters params = super.prepareModelParameters();
                        params._max_depth = 10;
                        params._min_rows = 10;
                        return params;
                    }
                },
                new GBMModelStep("def_5", aml()) {
                    @Override
                    public GBMParameters prepareModelParameters() {
                        GBMParameters params = super.prepareModelParameters();
                        params._max_depth = 15;
                        params._min_rows = 100;
                        return params;
                    }
                },
        };

        static class DefaultGBMGridStep extends GBMGridStep {
            public DefaultGBMGridStep(String id, AutoML autoML) {
                super(id, autoML);
            }

            @Override
            public Map<String, Object[]> prepareSearchParameters() {
                Map<String, Object[]> searchParams = new HashMap<>();
                searchParams.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
                searchParams.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
//                        searchParams.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
                searchParams.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00});
                searchParams.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0});
                searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0});
                searchParams.put("_min_split_improvement", new Double[]{1e-4, 1e-5});
                return searchParams;
            }
        }
        
        private final ModelingStep[] grids = new GBMGridStep[] {
                new DefaultGBMGridStep("grid_1", aml()),
/*
                new DefaultGBMGridStep("grid_1_resume", aml()) {
                    @Override
                    protected void setSearchCriteria(RandomDiscreteValueSearchCriteria searchCriteria, Model.Parameters baseParms) {
                        super.setSearchCriteria(searchCriteria, baseParms);
                        searchCriteria.set_stopping_rounds(0);
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected Job<Grid> startJob() {
                        Key<Grid>[] resumedGrid = aml().getResumableKeys(_provider, "grid_1");
                        if (resumedGrid.length == 0) return null;
                        return hyperparameterSearch(resumedGrid[0], prepareModelParameters(), prepareSearchParameters());
                    }
                }
*/
        };


        private final ModelingStep[] exploitation = new ModelingStep[] {
                new GBMExploitationStep("lr_annealing", aml()) {

                    Key<Models> resultKey = null;

                    @Override
                    protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
                        resultKey = result;
                        GBMModel bestGBM = getBestGBM();
                        aml().eventLog().info(EventLogEntry.Stage.ModelSelection, "Retraining best GBM with learning rate annealing: "+bestGBM._key);
                        GBMParameters params = (GBMParameters) bestGBM._input_parms.clone();
                        params._max_runtime_secs = 0; // reset max runtime
                        params._learn_rate_annealing = 0.99;
                        initTimeConstraints(params, maxRuntimeSecs);
                        setStoppingCriteria(params, new GBMParameters());
                        return asModelsJob(startModel(Key.make(result+"_model"), params), result);
                    }

                    @Override
                    protected ModelSelectionStrategy getSelectionStrategy() {
                        return (originalModels, newModels) ->
                                new KeepBestN<>(1, () -> makeTmpLeaderboard(Objects.toString(resultKey, _provider+"_"+_id)))
                                        .select(new Key[] { getBestGBM()._key }, newModels);
                    }
                }
        };

        public GBMSteps(AutoML autoML) {
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
        protected ModelingStep[] getGrids() {
            return grids;
        }

        @Override
        protected ModelingStep[] getOptionals() {
            return exploitation;
        }
    }

    @Override
    public String getName() {
        return GBMSteps.NAME;
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

