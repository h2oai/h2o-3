import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomMaeFunc, dataset_prostate
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def custom_mae_mm():
    return h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")


def assert_same_scoring_history(model_actual, model_expected, metric_name1, metric_name2, msg=None):
    scoring_history_actual = model_actual.scoring_history()
    scoring_history_expected = model_expected.scoring_history()
    sh1 = scoring_history_actual[metric_name1]
    sh2 = scoring_history_expected[metric_name2]
    assert (sh1.isnull() == sh2.isnull()).all(), msg
    assert (sh1.dropna() == sh2.dropna()).all(), msg


def test_custom_metric_early_stopping():
    (ftrain, fvalid, _) = dataset_prostate()
    model_expected = H2OGradientBoostingEstimator(model_id="prostate", ntrees=1000, max_depth=5,
                                                  score_each_iteration=True,
                                                  stopping_metric="mae",
                                                  stopping_tolerance=0.1,
                                                  stopping_rounds=3,
                                                  seed=123)
    model_expected.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    model_actual = H2OGradientBoostingEstimator(model_id="prostate", ntrees=1000, max_depth=5,
                                                score_each_iteration=True,
                                                custom_metric_func=custom_mae_mm(),
                                                stopping_metric="custom",
                                                stopping_tolerance=0.1,
                                                stopping_rounds=3,
                                                seed=123)
    model_actual.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)

    assert_same_scoring_history(model_actual, model_expected, "training_custom", "training_mae")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_custom_metric_early_stopping)
else:
    test_custom_metric_early_stopping()
