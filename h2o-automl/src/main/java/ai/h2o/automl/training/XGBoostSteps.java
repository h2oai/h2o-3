package ai.h2o.automl.training;

import ai.h2o.automl.*;
import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.xgboost.XGBoostModel;
import water.Job;
import water.Key;

public class XGBoostSteps extends TrainingSteps {

    abstract class XGBoostStep extends TrainingStep<XGBoostModel> {
        public XGBoostStep(String id) {
            super(id);
        }

        @Override
        protected Job makeJob() {
            return null;
        }
    }


    private TrainingStep[] defaults = new TrainingStep[] {
            new XGBoostStep("def_1") {
                @Override
                protected Job makeJob() {
                    Algo algo = Algo.XGBoost;
                    WorkAllocations.Work work = workAllocations.getAllocation(algo, AutoML.JobType.ModelBuild);
                    if (work == null) return;

                    XGBoostModel.XGBoostParameters commonXGBoostParameters = new XGBoostModel.XGBoostParameters();

                    Job xgBoostJob;
                    Key<Model> key;

                    if (emulateLightGBM) {
                        commonXGBoostParameters._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
                        commonXGBoostParameters._grow_policy = XGBoostModel.XGBoostParameters.GrowPolicy.lossguide;
                    }

                    // setDistribution: no way to identify gaussian, poisson, laplace? using descriptive statistics?
                    commonXGBoostParameters._distribution = getResponseColumn().isBinary() && !(getResponseColumn().isNumeric()) ? DistributionFamily.bernoulli
                            : getResponseColumn().isCategorical() ? DistributionFamily.multinomial
                            : DistributionFamily.AUTO;

                    commonXGBoostParameters._score_tree_interval = 5;
                    commonXGBoostParameters._stopping_rounds = 5;

                    commonXGBoostParameters._ntrees = 10000;
                    commonXGBoostParameters._learn_rate = 0.05;
//    commonXGBoostParameters._min_split_improvement = 0.01f;

                    //XGB 1 (medium depth)
                    XGBoostModel.XGBoostParameters xgBoostParameters = (XGBoostModel.XGBoostParameters) commonXGBoostParameters.clone();
                    xgBoostParameters._max_depth = 10;
                    xgBoostParameters._min_rows = 5;
                    xgBoostParameters._sample_rate = 0.6;
                    xgBoostParameters._col_sample_rate = 0.8;
                    xgBoostParameters._col_sample_rate_per_tree = 0.8;

                    if (emulateLightGBM) {
                        xgBoostParameters._max_leaves = 1 << xgBoostParameters._max_depth;
                        xgBoostParameters._max_depth = xgBoostParameters._max_depth * 2;
//      xgBoostParameters._min_data_in_leaf = (float) xgBoostParameters._min_rows;
                        xgBoostParameters._min_sum_hessian_in_leaf = (float) xgBoostParameters._min_rows;
                    }

                    key = modelKey(algo.name());
                    xgBoostJob = trainModel(key, work, xgBoostParameters);
                }
            },
            new XGBoostStep("def_2") {
            },
            new XGBoostStep("def_3") {
            }
    };

    private TrainingStep[] grids = new TrainingStep[] {
            new TrainingStep("grid_1") {
            }
    };

    @Override
    protected TrainingStep[] getDefaultModels() {
        return defaults;
    }

    @Override
    protected TrainingStep[] getGrids() {
        return grids;
    }
}
