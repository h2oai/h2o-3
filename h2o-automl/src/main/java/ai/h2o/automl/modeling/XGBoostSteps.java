package ai.h2o.automl.modeling;

import ai.h2o.automl.*;
import ai.h2o.automl.ModelSelectionStrategies.KeepBestN;
import ai.h2o.automl.events.EventLogEntry;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria.SequentialSearchCriteria;
import hex.grid.HyperSpaceSearchCriteria.StoppingCriteria;
import hex.grid.SequentialWalker;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import water.Job;
import water.Key;

import java.util.*;

import static ai.h2o.automl.ModelingStep.GridStep.DEFAULT_GRID_TRAINING_WEIGHT;
import static ai.h2o.automl.ModelingStep.ModelStep.DEFAULT_MODEL_TRAINING_WEIGHT;

public class XGBoostSteps extends ModelingSteps {

    static XGBoostParameters prepareModelParameters(AutoML aml, boolean emulateLightGBM) {
        XGBoostParameters xgBoostParameters = new XGBoostParameters();

        if (emulateLightGBM) {
            xgBoostParameters._tree_method = XGBoostParameters.TreeMethod.hist;
            xgBoostParameters._grow_policy = XGBoostParameters.GrowPolicy.lossguide;
        }

        // setDistribution: no way to identify gaussian, poisson, laplace? using descriptive statistics?
        xgBoostParameters._distribution = aml.getResponseColumn().isBinary() && !(aml.getResponseColumn().isNumeric()) ? DistributionFamily.bernoulli
                : aml.getResponseColumn().isCategorical() ? DistributionFamily.multinomial
                : DistributionFamily.AUTO;

        xgBoostParameters._score_tree_interval = 5;
        xgBoostParameters._ntrees = 10000;
//        xgBoostParameters._min_split_improvement = 0.01f;

        return xgBoostParameters;
    }

    static abstract class XGBoostModelStep extends ModelingStep.ModelStep<XGBoostModel> {

        boolean _emulateLightGBM;

        XGBoostModelStep(String id, int weight, AutoML autoML, boolean emulateLightGBM) {
            super(Algo.XGBoost, id, weight, autoML);
            _emulateLightGBM = emulateLightGBM;
        }

        XGBoostParameters prepareModelParameters() {
            return XGBoostSteps.prepareModelParameters(aml(), _emulateLightGBM);
        }
    }

    static abstract class XGBoostGridStep extends ModelingStep.GridStep<XGBoostModel> {
        boolean _emulateLightGBM;

        public XGBoostGridStep(String id, int weight, AutoML autoML, boolean emulateLightGBM) {
            super(Algo.XGBoost, id, weight, autoML);
            _emulateLightGBM = emulateLightGBM;
        }

        XGBoostParameters prepareModelParameters() {
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
        protected boolean canRun() {
            return super.canRun() && getBestXGBs(1).size() > 0;
        }
        public XGBoostExploitationStep(String id, int weight, AutoML autoML, boolean emulateLightGBM) {
            super(Algo.XGBoost, id, weight, autoML);
            _emulateLightGBM = emulateLightGBM;
        }
    }


    private ModelingStep[] defaults = new XGBoostModelStep[] {
            new XGBoostModelStep("def_1", DEFAULT_MODEL_TRAINING_WEIGHT, aml(),false) {
                @Override
                protected Job<XGBoostModel> startJob() {
                    //XGB 1 (medium depth)
                    XGBoostParameters xgBoostParameters = prepareModelParameters();
                    xgBoostParameters._max_depth = 10;
                    xgBoostParameters._min_rows = 5;
                    xgBoostParameters._sample_rate = 0.6;
                    xgBoostParameters._col_sample_rate = 0.8;
                    xgBoostParameters._col_sample_rate_per_tree = 0.8;

                    if (_emulateLightGBM) {
                        xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
                        xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
                    }

                    return trainModel(xgBoostParameters);
                }
            },
            new XGBoostModelStep("def_2", DEFAULT_MODEL_TRAINING_WEIGHT, aml(), false) {
                @Override
                protected Job<XGBoostModel> startJob() {
                    //XGB 2 (deep)
                    XGBoostParameters xgBoostParameters = prepareModelParameters();
                    xgBoostParameters._max_depth = 20;
                    xgBoostParameters._min_rows = 10;
                    xgBoostParameters._sample_rate = 0.6;
                    xgBoostParameters._col_sample_rate = 0.8;
                    xgBoostParameters._col_sample_rate_per_tree = 0.8;

                    if (_emulateLightGBM) {
                        xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
                        xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
                    }

                    return trainModel(xgBoostParameters);
                }
            },
            new XGBoostModelStep("def_3", DEFAULT_MODEL_TRAINING_WEIGHT, aml(), false) {
                @Override
                protected Job<XGBoostModel> startJob() {
                    //XGB 3 (shallow)
                    XGBoostParameters xgBoostParameters = prepareModelParameters();
                    xgBoostParameters._max_depth = 5;
                    xgBoostParameters._min_rows = 3;
                    xgBoostParameters._sample_rate = 0.8;
                    xgBoostParameters._col_sample_rate = 0.8;
                    xgBoostParameters._col_sample_rate_per_tree = 0.8;

                    if (_emulateLightGBM) {
                        xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
                        xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
                    }

                    return trainModel(xgBoostParameters);
                }
            },
    };

    private ModelingStep[] grids = new XGBoostGridStep[] {
            new XGBoostGridStep("grid_1", 5* DEFAULT_GRID_TRAINING_WEIGHT, aml(), false) {
                @Override
                protected Job<Grid> startJob() {
                    XGBoostParameters xgBoostParameters = prepareModelParameters();
                    Map<String, Object[]> searchParams = new HashMap<>();
//                    searchParams.put("_ntrees", new Integer[]{100, 1000, 10000}); // = _n_estimators

                    if (_emulateLightGBM) {
                        searchParams.put("_max_leaves", new Integer[]{1<<5, 1<<10, 1<<15, 1<<20});
                        searchParams.put("_max_depth", new Integer[]{10, 20, 50});
                    } else {
                        searchParams.put("_max_depth", new Integer[]{5, 10, 15, 20});
                        if (aml().getWeightsColumn() == null || aml().getWeightsColumn().isInt()) {
                            searchParams.put("_min_rows", new Double[]{1.0, 3.0, 5.0, 10.0, 15.0, 20.0});  // = _min_child_weight
                        } else {
                            searchParams.put("_min_rows", new Double[]{0.01, 0.1, 1.0, 3.0, 5.0, 10.0, 15.0, 20.0});  // = _min_child_weight
                        }
                    }
                    searchParams.put("_sample_rate", new Double[]{0.6, 0.8, 1.0}); // = _subsample
                    searchParams.put("_col_sample_rate" , new Double[]{ 0.6, 0.8, 1.0}); // = _colsample_bylevel"
                    searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.7, 0.8, 0.9, 1.0}); // = _colsample_bytree: start higher to always use at least about 40% of columns
//                    searchParams.put("_min_split_improvement", new Float[]{0.01f, 0.05f, 0.1f, 0.5f, 1f, 5f, 10f, 50f}); // = _gamma
//                    searchParams.put("_tree_method", new XGBoostParameters.TreeMethod[]{XGBoostParameters.TreeMethod.auto});
                    searchParams.put("_booster", new XGBoostParameters.Booster[]{ // include gblinear? cf. https://0xdata.atlassian.net/browse/PUBDEV-7254
                            XGBoostParameters.Booster.gbtree, //default, let's use it more often: note that some combinations may be trained multiple time by the RGS then.
                            XGBoostParameters.Booster.gbtree,
                            XGBoostParameters.Booster.dart
                    });

                    searchParams.put("_reg_lambda", new Float[]{0.001f, 0.01f, 0.1f, 1f, 10f, 100f});
                    searchParams.put("_reg_alpha", new Float[]{0.001f, 0.01f, 0.1f, 0.5f, 1f});

                    return hyperparameterSearch(xgBoostParameters, searchParams);
                }
            },
    };

    private ModelingStep[] exploitation = new ModelingStep[] {
            new XGBoostExploitationStep("lr_annealing", DEFAULT_MODEL_TRAINING_WEIGHT, aml(), false) {

                Key<Models> resultKey = null;

                @Override
                protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
                    resultKey = result;
                    XGBoostModel bestXGB = getBestXGB();
                    aml().eventLog().info(EventLogEntry.Stage.ModelSelection, "Retraining best XGBoost with learning rate annealing: "+bestXGB._key);
                    XGBoostParameters xgBoostParameters = (XGBoostParameters) bestXGB._parms.clone();
                    xgBoostParameters._ntrees = 10000; // reset ntrees (we'll need more for this fine-tuning)
                    xgBoostParameters._max_runtime_secs = 0; // reset max runtime
                    xgBoostParameters._learn_rate_annealing = 0.99;
                    initTimeConstraints(xgBoostParameters, maxRuntimeSecs);
                    setStoppingCriteria(xgBoostParameters, new XGBoostParameters());
                    return asModelsJob(startModel(Key.make(result+"_model"), xgBoostParameters), result);
                }

                @Override
                protected ModelSelectionStrategy getSelectionStrategy() {
                    return (originalModels, newModels) ->
                            new KeepBestN<>(1, () -> makeTmpLeaderboard(Objects.toString(resultKey, _algo+"_"+_id)))
                                    .select(new Key[] { getBestXGB()._key }, newModels);
                }
            },

            new XGBoostExploitationStep("lr_search", 2*DEFAULT_GRID_TRAINING_WEIGHT, aml(), false) {

                Key resultKey = null;

                @Override
                protected ModelSelectionStrategy getSelectionStrategy() {
                    return (originalModels, newModels) ->
                            new KeepBestN<>(1, () -> makeTmpLeaderboard(Objects.toString(resultKey, _algo+"_"+_id)))
                                    .select(new Key[] { getBestXGB()._key }, newModels);
                }

                @Override
                protected Job<Models> startTraining(Key result, double maxRuntimeSecs) {
                    resultKey = result;
                    XGBoostModel bestXGB = getBestXGBs(1).get(0);
                    aml().eventLog().info(EventLogEntry.Stage.ModelSelection, "Applying learning rate search on best XGBoost: "+bestXGB._key);
                    XGBoostParameters xgBoostParameters = (XGBoostParameters) bestXGB._parms.clone();
                    XGBoostParameters defaults = new XGBoostParameters();
                    xgBoostParameters._ntrees = 10000; // reset ntrees (we'll need more for this fine tuning)
                    xgBoostParameters._max_runtime_secs = 0; // reset max runtime
                    initTimeConstraints(xgBoostParameters, 0); // ensure we have a max runtime per model in the grid
                    setStoppingCriteria(xgBoostParameters, defaults); // keep the same seed as the bestXGB
                    // reset _eta to defaults, otherwise it ignores the _learn_rate hyperparam: this is very annoying!
                    xgBoostParameters._eta = defaults._eta;
//                    xgBoostParameters._learn_rate = defaults._learn_rate;

                    // keep stopping_rounds fixed, but increases score_tree_interval when lowering learn rate
                    int sti = xgBoostParameters._score_tree_interval;

                    Object[][] hyperParams = new Object[][] {
                            new Object[] {"_learn_rate", "_score_tree_interval"},
                            new Object[] {       0.5   ,                   sti },
                            new Object[] {       0.2   ,                 2*sti },
                            new Object[] {       0.1   ,                 3*sti },
                            new Object[] {       0.05  ,                 4*sti },
                            new Object[] {       0.02  ,                 5*sti },
                            new Object[] {       0.01  ,                 6*sti },
//                            new Object[] {       0.005 ,                 7*sti },
//                            new Object[] {       0.002 ,                 8*sti },
//                            new Object[] {       0.001 ,                 9*sti },
//                            new Object[] {       0.0005,                10*sti },
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
                            .setNamedValue("start_"+_algo+"_"+_id, new Date(), EventLogEntry.epochFormat.get());
                    return asModelsJob(GridSearch.startGridSearch(
                            Key.make(result+"_grid"),
                            new SequentialWalker<>(
                                    xgBoostParameters,
                                    hyperParams,
                                    new GridSearch.SimpleParametersBuilderFactory<>(),
                                    new SequentialSearchCriteria(StoppingCriteria.create()
                                            .maxRuntimeSecs((int)maxRuntimeSecs)
                                            .stoppingMetric(xgBoostParameters._stopping_metric)
                                            .stoppingRounds(2) // enforcing this as we define the sequence and it is quite small.
                                            .stoppingTolerance(xgBoostParameters._stopping_tolerance)
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
