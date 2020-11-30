from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def random_grid_model_seeds_early_stopping_case():
    air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"),
                              destination_frame="air.hex")
    
    features = ["Year", "Month", "CRSDepTime", "UniqueCarrier", "Origin", "Dest"]

    gbm1 = H2OGradientBoostingEstimator(
        nfolds=0,
        keep_cross_validation_models=True,
        keep_cross_validation_predictions=False,
        keep_cross_validation_fold_assignment=False,
        score_each_iteration=False,
        score_tree_interval=1,  # has to be set to reproduce early stopping
        ignore_const_cols=True,
        balance_classes=False,
        max_after_balance_size=5.0,
        max_confusion_matrix_size=20,
        ntrees=50,
        max_depth=10,
        min_rows=2.0,
        nbins=16,
        nbins_top_level=1024,
        nbins_cats=64,
        r2_stopping=1.7976931348623157e+308,
        stopping_rounds=0,
        stopping_tolerance=0.001,
        max_runtime_secs=1.7976931348623157e+308,
        seed=1249,
        build_tree_one_node=False,
        learn_rate=0.1,
        learn_rate_annealing=1.0,
        distribution='bernoulli',
        quantile_alpha=0.5,
        tweedie_power=1.5,
        huber_alpha=0.9,
        sample_rate=0.75,
        col_sample_rate=0.31,
        col_sample_rate_change_per_level=0.94,
        col_sample_rate_per_tree=0.65,
        min_split_improvement=0.0001,
        histogram_type='QuantilesGlobal',
        max_abs_leafnode_pred=1.7976931348623157e+308,
        pred_noise_bandwidth=0.0,
        calibrate_model=False,
        check_constant_response=True
    )
    gbm1.train(x=features, y="IsDepDelayed", training_frame=air_hex)

    gbm2 = H2OGradientBoostingEstimator(
        nfolds=0,
        keep_cross_validation_models=True,
        keep_cross_validation_predictions=False,
        keep_cross_validation_fold_assignment=False,
        score_each_iteration=False,
        score_tree_interval=1,  # has to be set to reproduce early stopping
        ignore_const_cols=True,
        balance_classes=False,
        max_after_balance_size=5.0,
        max_confusion_matrix_size=20,
        ntrees=50,
        max_depth=10,
        min_rows=2.0,
        nbins=16,
        nbins_top_level=1024,
        nbins_cats=64,
        r2_stopping=1.7976931348623157e+308,
        stopping_rounds=0,
        stopping_tolerance=0.001,
        max_runtime_secs=1.7976931348623157e+308,
        seed=1249,
        build_tree_one_node=False,
        learn_rate=0.1,
        learn_rate_annealing=1.0,
        distribution='bernoulli',
        quantile_alpha=0.5,
        tweedie_power=1.5,
        huber_alpha=0.9,
        sample_rate=0.75,
        col_sample_rate=0.31,
        col_sample_rate_change_per_level=0.94,
        col_sample_rate_per_tree=0.65,
        min_split_improvement=0.0001,
        histogram_type='QuantilesGlobal',
        max_abs_leafnode_pred=1.7976931348623157e+308,
        pred_noise_bandwidth=0.0,
        calibrate_model=False,
        check_constant_response=True
    )
    gbm2.train(x=features, y="IsDepDelayed", training_frame=air_hex)

    rmse1 = pyunit_utils.extract_from_twoDimTable(gbm1._model_json["output"]["scoring_history"], "training_rmse", False)
    rmse2 = pyunit_utils.extract_from_twoDimTable(gbm2._model_json["output"]["scoring_history"], "training_rmse", False)
    print(rmse1)
    print(rmse2)

    assert pyunit_utils.equal_two_arrays(rmse1, rmse2, 1e-5, 1e-6, False), \
        "Training_rmse are different between the two grid search models.  Tests are supposed to be repeatable in " \
        "this case.  Make sure model seeds are actually set correctly in the Java backend."


if __name__ == "__main__":
    pyunit_utils.standalone_test(random_grid_model_seeds_early_stopping_case)
else:
    random_grid_model_seeds_early_stopping_case()
