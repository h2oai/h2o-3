import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import time
from h2o.estimators.xgboost import H2OXGBoostEstimator


def test_gam_model_predict():
    loan_df = h2o.import_file(pyunit_utils.locate("bigdata/laptop/lending-club/loan.csv"))
    y = 'bad_loan'
    loan_df[y] = loan_df[y].asfactor()

    xgb_classic_start = time.time()
    xgb_classic = H2OXGBoostEstimator(distribution='bernoulli', max_depth=3,
                                      ntrees=1000,
                                      score_tree_interval=3,
                                      stopping_metric="logloss",
                                      stopping_rounds=3,
                                      stopping_tolerance=1e-2,
                                      seed=42,
                                      nfolds=5)
    xgb_classic.train(y=y, training_frame=loan_df)
    xgb_classic_end = time.time()

    xgb_eval_metric_start = time.time()
    xgb_eval_metric = H2OXGBoostEstimator(distribution='bernoulli', max_depth=3,
                                          ntrees=1000,
                                          score_tree_interval=3,
                                          score_eval_metric_only=True,
                                          eval_metric="logloss",
                                          stopping_metric="custom",
                                          stopping_rounds=3,
                                          stopping_tolerance=1e-2,
                                          seed=42,
                                          nfolds=5)
    xgb_eval_metric.train(y=y, training_frame=loan_df)
    xgb_eval_metric_end = time.time()

    assert xgb_classic.summary()["number_of_trees"] == xgb_eval_metric.summary()["number_of_trees"]

    # the CV models should be identical
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(
        xgb_classic.cross_validation_metrics_summary(),
        xgb_eval_metric.cross_validation_metrics_summary(),
        col_header_list=["mean", "sd", "cv_1_valid", "cv_2_valid", "cv_3_valid", "cv_4_valid", "cv_5_valid"]
    )

    # note: this comparison is not fair (second xgb run will benefit from the first run) and doesn't prove anything
    # it should however hold in test
    print("Duration with classic scoring: %s" % (xgb_classic_end - xgb_classic_start))
    print("Duration with eval_metric: %s" % (xgb_eval_metric_end - xgb_eval_metric_start))
    assert (xgb_classic_end - xgb_classic_start) > (xgb_eval_metric_end - xgb_eval_metric_start)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
