from __future__ import print_function

from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.xgboost import H2OXGBoostEstimator
from tests import pyunit_utils


def test_tree_based_model_early_stopping_warnings(estimator):

    def test_no_warning_score_tree_interval():
        training_data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))

        with pyunit_utils.catch_warnings() as ws:
            model = estimator(stopping_rounds=1, stopping_metric="mse", score_tree_interval=3)
            model.train(x=list(range(13)), y=13, training_frame=training_data)
            assert pyunit_utils.no_warnings(ws)

    def test_no_warning_score_each_iteration():
        training_data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))

        with pyunit_utils.catch_warnings() as ws:
            model = estimator(stopping_rounds=1, stopping_metric="mse", score_each_iteration=True)
            model.train(x=list(range(13)), y=13, training_frame=training_data)
            assert pyunit_utils.no_warnings(ws)

    def test_reproducible_early_stopping_warning():
        training_data = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))
    
        with pyunit_utils.catch_warnings() as ws:
            model = estimator(stopping_rounds=1, stopping_metric="mse")
            model.train(x=list(range(13)), y=13, training_frame=training_data)
            expected_message = 'early stopping is enabled but neither score_tree_interval or ' \
                               'score_each_iteration are defined. Early stopping will not be reproducible!'
            assert pyunit_utils.contains_warning(ws, expected_message)

    return [test_no_warning_score_each_iteration, 
            test_no_warning_score_tree_interval, 
            test_reproducible_early_stopping_warning
            ]


pyunit_utils.run_tests([
    test_tree_based_model_early_stopping_warnings(H2OGradientBoostingEstimator),
    test_tree_based_model_early_stopping_warnings(H2ORandomForestEstimator),
    test_tree_based_model_early_stopping_warnings(H2OXGBoostEstimator)
])
