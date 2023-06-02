import sys
import math

sys.path.insert(1, "../../../")
from tests import pyunit_utils
from tests.pyunit_utils import dataset_prostate
from h2o.estimators.xgboost import H2OXGBoostEstimator


def test_eval_metric():
    # The idea of this test to show we can fully customize XGBoost's evaluation metric
    # by first training a regular XGBoost model without eval_metric and extract F1-optimal threshold
    # We then pass this threshold to second XGBoost model's evaluation metric 'error@threshold'
    # This means the second model will use the same threshold for xgboost eval_metric as H2O
    # and the training_classification_error will thus match XGBoost's eval_metric (in the last iteration)
    (train, _, _) = dataset_prostate()

    model = H2OXGBoostEstimator(ntrees=10, max_depth=4,
                                score_each_iteration=True,
                                seed=123)
    model.train(y="CAPSULE", x=train.names, training_frame=train)
    threshold = model._model_json['output']['default_threshold']

    scale = 1e5
    xgb_threshold = math.floor(threshold * scale) / scale
    
    eval_metric = "error@%s" % xgb_threshold
    print("Eval metric = " + eval_metric)

    model_eval = H2OXGBoostEstimator(ntrees=10, max_depth=4,
                                     score_each_iteration=True,
                                     eval_metric=eval_metric,
                                     seed=123)
    model_eval.train(y="CAPSULE", x=train.names, training_frame=train)
    print(model_eval.scoring_history())

    h2o_error = model.scoring_history()['training_classification_error']
    h2o_error_last = h2o_error.iat[-1]
    xgb_error = model_eval.scoring_history()['training_custom']
    xgb_error_last = xgb_error.iat[-1]
    assert abs(h2o_error_last - xgb_error_last) < 1e-5


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_eval_metric)
else:
    test_eval_metric()
