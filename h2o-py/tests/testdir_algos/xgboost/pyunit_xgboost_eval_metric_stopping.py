import sys

sys.path.insert(1, "../../../")
from h2o import upload_custom_metric
from tests import pyunit_utils
from tests.pyunit_utils import dataset_prostate
from h2o.estimators.xgboost import H2OXGBoostEstimator
from tests.pyunit_utils import CustomMaeFunc


def custom_mae_mm():
    return upload_custom_metric(CustomMaeFunc, func_name="mae-custom", func_file="mm_mae.py")


def assert_same_scoring_history(model_actual, model_expected, metric_name1, metric_name2, msg=None):
    scoring_history_actual = model_actual.scoring_history()
    scoring_history_expected = model_expected.scoring_history()
    sh1 = scoring_history_actual[metric_name1]
    sh2 = scoring_history_expected[metric_name2]
    assert (sh1 - sh2).abs().max() < 1e-4, msg


def check_eval_metric_early_stopping(score_eval_metric_only=False, eval_metric=None, custom_metric_func=None):
    (train, _, _) = dataset_prostate()
    model_expected = H2OXGBoostEstimator(model_id="prostate_mae", ntrees=1000, max_depth=5,
                                         score_each_iteration=True,
                                         stopping_metric="mae",
                                         stopping_tolerance=0.1,
                                         stopping_rounds=3,
                                         seed=123)
    model_expected.train(y="AGE", x=train.names, training_frame=train)

    model_actual = H2OXGBoostEstimator(model_id="prostate_custom", ntrees=1000, max_depth=5,
                                       score_each_iteration=True,
                                       score_eval_metric_only=score_eval_metric_only,
                                       eval_metric=eval_metric,
                                       custom_metric_func=custom_metric_func,
                                       stopping_metric="custom",
                                       stopping_tolerance=0.1,
                                       stopping_rounds=3,
                                       seed=123)
    model_actual.train(y="AGE", x=train.names, training_frame=train)

    assert_same_scoring_history(model_actual, model_expected, "training_custom", "training_mae")

    return model_actual.scoring_history()


def test_eval_metric_early_stopping():
    check_eval_metric_early_stopping(eval_metric="mae")


def test_eval_metric_early_stopping_native_scoring_only():
    history = check_eval_metric_early_stopping(True, eval_metric="mae")
    for col in history.columns:
        if col.startswith("training_") and col != "training_custom":
            is_missing = history[col].isnull()
            # scoring history is not defined for H2O metrics...
            assert is_missing.sum() == len(is_missing) - 1
            # ...except for the final scoring metric
            assert not is_missing.iloc[-1]


def test_custom_metric_early_stopping():
    check_eval_metric_early_stopping(custom_metric_func=custom_mae_mm())


pyunit_utils.run_tests([
    test_eval_metric_early_stopping,
    test_eval_metric_early_stopping_native_scoring_only,
    test_custom_metric_early_stopping
])
