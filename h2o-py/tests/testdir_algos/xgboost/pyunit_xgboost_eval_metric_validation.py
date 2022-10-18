import sys

sys.path.insert(1, "../../../")
from tests import pyunit_utils
from tests.pyunit_utils import dataset_prostate
from h2o.estimators.xgboost import H2OXGBoostEstimator


def assert_same_scoring_history(model, metric_name1, metric_name2, msg=None):
    sh = model.scoring_history()
    sh1 = sh[metric_name1]
    sh2 = sh[metric_name2]
    assert (sh1 - sh2).abs().max() < 1e-4, msg


def test_eval_metric_with_validation():
    (train, valid, _) = dataset_prostate()

    model = H2OXGBoostEstimator(ntrees=10, max_depth=4,
                                score_each_iteration=True,
                                eval_metric="logloss",
                                seed=123)
    model.train(y="CAPSULE", x=train.names, training_frame=train, validation_frame=valid)

    assert_same_scoring_history(model, "training_logloss", "training_custom")
    assert_same_scoring_history(model, "validation_logloss", "validation_custom")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_eval_metric_with_validation)
else:
    test_eval_metric_with_validation()
