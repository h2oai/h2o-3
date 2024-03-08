package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.ModelSelectionStrategies.KeepBestN;
import ai.h2o.automl.events.EventLogEntry;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria.SequentialSearchCriteria;
import hex.grid.HyperSpaceSearchCriteria.StoppingCriteria;
import hex.grid.SequentialWalker;
import hex.grid.SimpleParametersBuilderFactory;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import water.Job;
import water.Key;

import java.util.*;
import java.util.stream.IntStream;

public class XGBoostSteps extends ModelingSteps {

    static final String NAME = Algo.XGBoost.name();
            
    static XGBoostParameters prepareModelParameters(AutoML aml, boolean emulateLightGBM) {
        XGBoostParameters params = new XGBoostParameters();

        if (emulateLightGBM) {
            params._tree_method = XGBoostParameters.TreeMethod.hist;
            params._grow_policy = XGBoostParameters.GrowPolicy.lossguide;
        }

        params._score_tree_interval = 5;
        params._ntrees = 10000;
//        params._min_split_improvement = 0.01f;

        return params;
    }

    static abstract class XGBoostModelStep extends ModelingStep.ModelStep<XGBoostModel> {

        boolean _emulateLightGBM;

        XGBoostModelStep(String id, AutoML autoML, boolean emulateLightGBM) {
            super(NAME, Algo.XGBoost, id, autoML);
            _emulateLightGBM = emulateLightGBM;
        }

        public XGBoostParameters prepareModelParameters() {
            XGBoostParameters params =  XGBoostSteps.prepareModelParameters(aml(), _emulateLightGBM);
            if (aml().getBuildSpec().build_control.balance_classes && aml().getDistributionFamily().equals(DistributionFamily.bernoulli)) {
                double[] dist = aml().getClassDistribution();
                params._scale_pos_weight =  (float) (dist[0] / dist[1]);
            }
            return params;
        }
    }

    static abstract class XGBoostGridStep extends ModelingStep.GridStep<XGBoostModel> {
        boolean _emulateLightGBM;

        public XGBoostGridStep(String id, AutoML autoML, boolean emulateLightGBM) {
            super(NAME, Algo.XGBoost, id, autoML);
            _emulateLightGBM = emulateLightGBM;
        }

        public XGBoostParameters prepareModelParameters() {
            return XGBoostSteps.prepareModelParameters(aml(), _emulateLightGBM);
        }
    }

    static abstract class XGBoostExploitationStep extends ModelingStep.SelectionStep<XGBoostModel> {
        boolean _emulateLightGBM;

        protected XGBoostModel getBestXGB() {
            return getBestXGBs(1).get(0);
        }

        protected List<XGBoostModel> getBestXGBs(int topN) {
            List<XGBoostModel> xgbs = new ArrayList<>();
            for (Model model : getTrainedModels()) {
                if (model instanceof XGBoostModel) {
                    xgbs.add((XGBoostModel) model);
                }
                if (xgbs.size() == topN) break;
            }
            return xgbs;
        }

        @Override
        public boolean canRun() {
            return super.canRun() && getBestXGBs(1).size() > 0;
        }
        public XGBoostExploitationStep(String id, AutoML autoML, boolean emulateLightGBM) {
            super(NAME, Algo.XGBoost, id, autoML);
            _emulateLightGBM = emulateLightGBM;
            if (autoML.getBuildSpec().build_models.exploitation_ratio > 0)
                _ignoredConstraints = new AutoML.Constraint[] { AutoML.Constraint.MODEL_COUNT };
        }
    }


    private final ModelingStep[] defaults = new XGBoostModelStep[] {
            new XGBoostModelStep("def_1", aml(), false) {
                @Override
                public XGBoostParameters prepareModelParameters() {
                    //XGB 1 (medium depth)
                    XGBoostParameters params = super.prepareModelParameters();
                    params._max_depth = 10;
                    params._min_rows = 5;
                    params._sample_rate = 0.6;
                    params._col_sample_rate = 0.8;
                    params._col_sample_rate_per_tree = 0.8;

                    if (_emulateLightGBM) {
                        params._max_leaves = 1 << params._max_depth;
                        params._max_depth = params._max_depth * 2;
                    }

                    return params;
                }
            },
            new XGBoostModelStep("def_2", aml(), false) {
                @Override
                public XGBoostParameters prepareModelParameters() {
                    //XGB 2 (deep)
                    XGBoostParameters params = super.prepareModelParameters();
                    params._max_depth = 15;
                    params._min_rows = 10;
                    params._sample_rate = 0.6;
                    params._col_sample_rate = 0.8;
                    params._col_sample_rate_per_tree = 0.8;

                    if (_emulateLightGBM) {
                        params._max_leaves = 1 << params._max_depth;
                        params._max_depth = params._max_depth * 2;
                    }

                    return params;
                }
            },
            new XGBoostModelStep("def_3", aml(), false) {
                @Override
                public XGBoostParameters prepareModelParameters() {
                    //XGB 3 (shallow)
                    XGBoostParameters params = super.prepareModelParameters();
                    params._max_depth = 5;
                    params._min_rows = 3;
                    params._sample_rate = 0.8;
                    params._col_sample_rate = 0.8;
                    params._col_sample_rate_per_tree = 0.8;

                    if (_emulateLightGBM) {
                        params._max_leaves = 1 << params._max_depth;
                        params._max_depth = params._max_depth * 2;
                    }

                    return params;
                }
            },
    };

    
    static class DefaultXGBoostGridStep extends XGBoostGridStep {

        public DefaultXGBoostGridStep(String id, AutoML autoML) {
            super(id, autoML, false);
        }

        @Override
        public XGBoostParameters prepareModelParameters() {
            XGBoostParameters params = super.prepareModelParameters();
            // Reset scale pos weight so we can grid search the parameter
            params._scale_pos_weight = (new XGBoostParameters())._scale_pos_weight;
            return params;
        }

        @Override
        public Map<String, Object[]> prepareSearchParameters() {
            Map<String, Object[]> searchParams = new HashMap<>();
//            searchParams.put("_ntrees", new Integer[]{100, 1000, 10000}); // = _n_estimators

            if (_emulateLightGBM) {
                searchParams.put("_max_leaves", new Integer[]{1 << 5, 1 << 10, 1 << 15, 1 << 20});
                searchParams.put("_max_depth", new Integer[]{10, 20, 50});
            } else {
                searchParams.put("_max_depth", new Integer[]{3, 6, 9, 12, 15});
                if (aml().getWeightsColumn() == null || aml().getWeightsColumn().isInt()) {
                    searchParams.put("_min_rows", new Double[]{1.0, 3.0, 5.0, 10.0, 15.0, 20.0});  // = _min_child_weight
                } else {
                    searchParams.put("_min_rows", new Double[]{0.01, 0.1, 1.0, 3.0, 5.0, 10.0, 15.0, 20.0});  // = _min_child_weight
                }
            }
            searchParams.put("_sample_rate", new Double[]{0.6, 0.8, 1.0}); // = _subsample
            searchParams.put("_col_sample_rate", new Double[]{0.6, 0.8, 1.0}); // = _colsample_bylevel"
            searchParams.put("_col_sample_rate_per_tree", new Double[]{0.7, 0.8, 0.9, 1.0}); // = _colsample_bytree: start higher to always use at least about 40% of columns
//            searchParams.put("_min_split_improvement", new Float[]{0.01f, 0.05f, 0.1f, 0.5f, 1f, 5f, 10f, 50f}); // = _gamma
//            searchParams.put("_tree_method", new XGBoostParameters.TreeMethod[]{XGBoostParameters.TreeMethod.auto});
            searchParams.put("_booster", new XGBoostParameters.Booster[]{ // include gblinear? cf. https://github.com/h2oai/h2o-3/issues/8381
                    XGBoostParameters.Booster.gbtree, //default, let's use it more often: note that some combinations may be trained multiple time by the RGS then.
                    XGBoostParameters.Booster.gbtree,
                    XGBoostParameters.Booster.dart
            });

            searchParams.put("_reg_lambda", new Float[]{0.001f, 0.01f, 0.1f, 1f, 10f, 100f});
            searchParams.put("_reg_alpha", new Float[]{0.001f, 0.01f, 0.1f, 0.5f, 1f});

            if (aml().getBuildSpec().build_control.balance_classes && aml().getDistributionFamily().equals(DistributionFamily.bernoulli)) {
                double[] dist = aml().getClassDistribution();
                final float negPosRatio = (float)(dist[0] / dist[1]);
                final float imbalanceRatio = negPosRatio < 1 ? 1 / negPosRatio : negPosRatio;
                searchParams.put("_scale_pos_weight", new Float[]{1.f, negPosRatio});
                searchParams.put("_max_delta_step",  new Float[]{0f, Math.min(5f, imbalanceRatio / 2), Math.min(10f, imbalanceRatio)});
            }
            return searchParams;
        }

    }

    static class XGBoostGBLinearGridStep extends XGBoostGridStep {
        
        public XGBoostGBLinearGridStep(String id, AutoML autoML) {
            super(id, autoML, false);
        }


        @Override
        public XGBoostParameters prepareModelParameters() {
            return XGBoostSteps.prepareModelParameters(aml(), false);
        }

        @Override
        public Map<String, Object[]> prepareSearchParameters() {
            Map<String, Object[]> searchParams = new HashMap<>();

            /* 
            // not supported/exposed in our xgboost yet
            if (aml().getBuildSpec().build_control.isReproducible()) {
                searchParams.put("_updater", new String[] {"coord_descent"});
                searchParams.put("_feature_selector", new String[] {"cyclic", "greedy"});  // TODO: check if others are deterministic
                            } else {
                searchParams.put("_updater", new String[] {"shotgun", "coord_descent"});
                searchParams.put("_feature_selector", new String[] {"cyclic", "shuffle", "random", "greedy", "thrifty"});
            }
            int ncols = aml().getTrainingFrame().numCols() - (aml().getBuildSpec().getNonPredictors().length +
                    (aml().getBuildSpec().input_spec.ignored_columns != null ? aml().getBuildSpec().input_spec.ignored_columns.length : 0));

            searchParams.put("_top_k", IntStream.range(0, ncols-1).boxed().toArray(Integer[]::new));
            */
            
            searchParams.put("_booster", new XGBoostParameters.Booster[]{ XGBoostParameters.Booster.gblinear });

            searchParams.put("_reg_lambda", new Float[]{0.001f, 0.01f, 0.1f, 1f, 10f, 100f});
            searchParams.put("_reg_alpha", new Float[]{0.001f, 0.01f, 0.1f, 0.5f, 1f});
            
            return searchParams;
        }

    }

    private final ModelingStep[] grids = new XGBoostGridStep[] {
            new DefaultXGBoostGridStep("grid_1", aml()),
            new XGBoostGBLinearGridStep("grid_gblinear", aml()),

/*
            new DefaultXGBoostGridStep("grid_1_resume", aml()) {
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
            new XGBoostExploitationStep("lr_annealing", aml(), false) {

                Key<Models> resultKey = null;

                @Override
                protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
                    resultKey = result;
                    XGBoostModel bestXGB = getBestXGB();
                    aml().eventLog().info(EventLogEntry.Stage.ModelSelection, "Retraining best XGBoost with learning rate annealing: "+bestXGB._key);
                    XGBoostParameters params = (XGBoostParameters) bestXGB._input_parms.clone();
                    params._max_runtime_secs = 0; // reset max runtime
                    params._learn_rate_annealing = 0.99;
                    initTimeConstraints(params, maxRuntimeSecs);
                    setStoppingCriteria(params, new XGBoostParameters());
                    return asModelsJob(startModel(Key.make(result+"_model"), params), result);
                }

                @Override
                protected ModelSelectionStrategy getSelectionStrategy() {
                    return (originalModels, newModels) ->
                            new KeepBestN<>(1, () -> makeTmpLeaderboard(Objects.toString(resultKey, _provider+"_"+_id)))
                                    .select(new Key[] { getBestXGB()._key }, newModels);
                }
            },

            new XGBoostExploitationStep("lr_search", aml(), false) {

                Key resultKey = null;

                @Override
                protected ModelSelectionStrategy getSelectionStrategy() {
                    return (originalModels, newModels) ->
                            new KeepBestN<>(1, () -> makeTmpLeaderboard(Objects.toString(resultKey, _provider+"_"+_id)))
                                    .select(new Key[] { getBestXGB()._key }, newModels);
                }

                @Override
                protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
                    resultKey = result;
                    XGBoostModel bestXGB = getBestXGBs(1).get(0);
                    aml().eventLog().info(EventLogEntry.Stage.ModelSelection, "Applying learning rate search on best XGBoost: "+bestXGB._key);
                    XGBoostParameters params = (XGBoostParameters) bestXGB._input_parms.clone();
                    XGBoostParameters defaults = new XGBoostParameters();
                    params._max_runtime_secs = 0; // reset max runtime
                    initTimeConstraints(params, 0); // ensure we have a max runtime per model in the grid
                    setStoppingCriteria(params, defaults); // keep the same seed as the bestXGB

                    // keep stopping_rounds fixed, but increases score_tree_interval when lowering learn rate
                    int sti = params._score_tree_interval;

                    Object[][] hyperParams = new Object[][] {
                            new Object[] {"_learn_rate", "_score_tree_interval"},
                            new Object[] {       0.5   ,                   sti },
                            new Object[] {       0.2   ,                 2*sti },
                            new Object[] {       0.1   ,                 3*sti },
                            new Object[] {       0.05  ,                 4*sti },
                            new Object[] {       0.02  ,                 5*sti },
                            new Object[] {       0.01  ,                 6*sti },
                            new Object[] {       0.005 ,                 7*sti },
                            new Object[] {       0.002 ,                 8*sti },
                            new Object[] {       0.001 ,                 9*sti },
                            new Object[] {       0.0005,                10*sti },
                    };

/*
                    Object[][] hyperParams = new Object[][] {
                            new Object[] {"_learn_rate", "_score_tree_interval"},
                            new Object[] {       0.5   ,                   sti },
                            new Object[] {       0.2   ,            (1<<1)*sti },
                            new Object[] {       0.1   ,            (1<<2)*sti },
                            new Object[] {       0.05  ,            (1<<3)*sti },
                            new Object[] {       0.02  ,            (1<<4)*sti },
                            new Object[] {       0.01  ,            (1<<5)*sti },
                            new Object[] {       0.005 ,            (1<<6)*sti },
                            new Object[] {       0.002 ,            (1<<7)*sti },
                            new Object[] {       0.001 ,            (1<<8)*sti },
                            new Object[] {       0.0005,            (1<<9)*sti },
                    };
*/

                    aml().eventLog().info(EventLogEntry.Stage.ModelTraining, "AutoML: starting "+resultKey+" model training")
                            .setNamedValue("start_"+_provider+"_"+_id, new Date(), EventLogEntry.epochFormat.get());
                    return asModelsJob(GridSearch.startGridSearch(
                            Key.make(result+"_grid"),
                            new SequentialWalker<>(
                                    params,
                                    hyperParams,
                                    new SimpleParametersBuilderFactory<>(),
                                    new SequentialSearchCriteria(StoppingCriteria.create()
                                            .maxRuntimeSecs((int)maxRuntimeSecs)
                                            .stoppingMetric(params._stopping_metric)
                                            .stoppingRounds(3) // enforcing this as we define the sequence and it is quite small.
                                            .stoppingTolerance(params._stopping_tolerance)
                                            .build())
                            ),
                            GridSearch.SEQUENTIAL_MODEL_BUILDING
                    ), result);
                }
            }
    };

    public XGBoostSteps(AutoML autoML) {
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
