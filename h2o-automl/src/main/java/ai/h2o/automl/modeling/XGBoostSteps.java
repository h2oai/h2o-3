package ai.h2o.automl.modeling;

import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoML;
import ai.h2o.automl.ModelingStep;
import ai.h2o.automl.ModelingSteps;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.grid.Grid;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostModel.XGBoostParameters;
import water.Job;

import java.util.HashMap;
import java.util.Map;

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
        xgBoostParameters._stopping_rounds = 5;

        xgBoostParameters._ntrees = 10000;
        xgBoostParameters._learn_rate = 0.05;
//            xgBoostParameters._min_split_improvement = 0.01f;

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
//                        xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
                        xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
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
//                        xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
                        xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
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
//                        xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
                        xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
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
                        searchParams.put("_min_sum_hessian_in_leaf", new Double[]{0.01, 0.1, 1.0, 3.0, 5.0, 10.0, 15.0, 20.0});
                    } else {
                        searchParams.put("_max_depth", new Integer[]{5, 10, 15, 20});
                        searchParams.put("_min_rows", new Double[]{0.01, 0.1, 1.0, 3.0, 5.0, 10.0, 15.0, 20.0});  // = _min_child_weight
                    }

                    searchParams.put("_sample_rate", new Double[]{0.6, 0.8, 1.0}); // = _subsample
                    searchParams.put("_col_sample_rate" , new Double[]{ 0.6, 0.8, 1.0}); // = _colsample_bylevel"
                    searchParams.put("_col_sample_rate_per_tree", new Double[]{ 0.7, 0.8, 0.9, 1.0}); // = _colsample_bytree: start higher to always use at least about 40% of columns
//                    searchParams.put("_learn_rate", new Double[]{0.01, 0.05, 0.08, 0.1, 0.15, 0.2, 0.3, 0.5, 0.8, 1.0}); // = _eta
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

    private ModelingStep[] exploitation = new XGBoostModelStep[] {
            new XGBoostModelStep("lr_annealing", DEFAULT_MODEL_TRAINING_WEIGHT, aml(), false) {

                private XGBoostModel getBestXGB() {
                    for (Model model : getTrainedModels()) {
                        if (model instanceof XGBoostModel) {
                            return (XGBoostModel) model;
                        }
                    }
                    return null;
                }

                @Override
                protected boolean canRun() {
                    // TODO: add event log message here?
                    return getBestXGB() != null;
                }

                @Override
                protected Job<XGBoostModel> startJob() {
                    XGBoostModel bestXGB = getBestXGB();
                    XGBoostParameters xgBoostParameters = (XGBoostParameters) bestXGB._parms.clone();
                    xgBoostParameters._learn_rate = 0.5;
                    xgBoostParameters._learn_rate_annealing = 0.9;
                    return trainModel(xgBoostParameters);
                }
            },

            new XGBoostModelStep("lr_search", DEFAULT_GRID_TRAINING_WEIGHT, aml(), false) {
                private XGBoostModel getBestXGB() {
                    for (Model model : getTrainedModels()) {
                        if (model instanceof XGBoostModel) {
                            return (XGBoostModel) model;
                        }
                    }
                    return null;
                }

                @Override
                protected boolean canRun() {
                    // TODO: add event log message here?
                    return getBestXGB() != null;
                }

                @Override
                protected Job<XGBoostModel> startJob() {
                    return null;
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
