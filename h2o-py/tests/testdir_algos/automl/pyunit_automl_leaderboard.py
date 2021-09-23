from __future__ import print_function
import sys, os

from h2o.exceptions import H2OValueError

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import random
import sys
from tests import pyunit_utils
from h2o.automl import H2OAutoML, get_automl, get_leaderboard

from pandas.util.testing import assert_frame_equal

"""
This test is used to check leaderboard, especially sorting logic and filtered algos
"""
# Random positive seed for AutoML
if sys.version_info[0] < 3: #Python 2
    automl_seed = random.randint(0, sys.maxint)
else: # Python 3
    automl_seed = random.randint(0, sys.maxsize)
print("Random Seed for pyunit_automl_leaderboard.py = " + str(automl_seed))

all_algos = ["DeepLearning", "DRF", "GBM", "GLM", "XGBoost", "StackedEnsemble"]


class Obj(object):
    pass


def prepare_data(type):
    ds = Obj()
    if type == 'binomial':
        ds.train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
        ds.target = "CAPSULE"
        ds.train[ds.target] = ds.train[ds.target].asfactor()
    elif type == 'multinomial':
        ds.train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
        ds.target = 4
    else: #regression
        ds.train = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"))
        ds.target = 'runoffnew'
    return ds


def get_partitioned_model_names(leaderboard):
    model_names = Obj()
    model_names.all = list(h2o.as_list(leaderboard['model_id'])['model_id'])
    model_names.se = [m for m in model_names.all if m.startswith('StackedEnsemble')]
    model_names.non_se = [m for m in model_names.all if m not in model_names.se]
    return model_names


def check_model_property(model_names, prop_name, present=True, actual_value=None, default_value=None):
    for mn in model_names:
        model = h2o.get_model(mn)
        if present:
            assert prop_name in model.params.keys(), \
                "missing {prop} in model {model}".format(prop=prop_name, model=mn)
            assert actual_value is None or model.params[prop_name]['actual'] == actual_value, \
                "actual value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['actual'], exp=actual_value)
            assert default_value is None or model.params[prop_name]['default'] == default_value, \
                "default value for {prop} in model {model} is {val}, expected {exp}".format(prop=prop_name, model=mn, val=model.params[prop_name]['default'], exp=default_value)
        else:
            assert prop_name not in model.params.keys(), "unexpected {prop} in model {model}".format(prop=prop_name, model=mn)


def check_leaderboard(aml, excluded_algos, expected_metrics, expected_sort_metric, expected_sorted_desc=False):
    print("AutoML leaderboard")
    leaderboard = aml.leaderboard
    print(leaderboard)
    # check that correct leaderboard columns exist
    expected_columns = (['model_id'] + expected_metrics)
    assert leaderboard.names == expected_columns, \
        "expected leaderboard columns to be {expected} but got {actual}".format(expected=expected_columns, actual=leaderboard.names)

    model_ids = list(h2o.as_list(leaderboard['model_id'])['model_id'])
    assert len([a for a in excluded_algos if len([b for b in model_ids if a in b]) > 0]) == 0, \
        "leaderboard contains some excluded algos among {excluded}: {models}".format(excluded=excluded_algos, models=model_ids)

    included_algos = list(set(all_algos) - set(excluded_algos)) + ([] if 'DRF' in excluded_algos else ['XRT'])
    assert len([a for a in included_algos if len([b for b in model_ids if a in b]) > 0]) == len(included_algos), \
        "leaderboard is missing some algos from {included}: {models}".format(included=included_algos, models=model_ids)

    j_leaderboard = aml._state_json['leaderboard']
    if expected_sort_metric is not None:
        sort_metric = j_leaderboard['sort_metric']
        assert sort_metric == expected_sort_metric, \
            "expected leaderboard sorted by {expected} but was sorted by {actual}".format(expected=expected_sort_metric, actual=sort_metric)
    if expected_sorted_desc is not None:
        sorted_desc = j_leaderboard['sort_decreasing']
        assert sorted_desc == expected_sorted_desc, \
            "expected leaderboard sorted {expected} but was sorted {actual}".format(expected="desc" if expected_sorted_desc else "asc",
                                                                                    actual="desc" if sorted_desc else "asc")


def test_warn_on_empty_leaderboard():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    y = 'CAPSULE'
    train[y] = train[y].asfactor()
    aml = H2OAutoML(project_name="test_empty_leaderboard",
                    include_algos=[],
                    seed=1234)
    aml.train(y=y, training_frame=train)
    assert aml.leaderboard.nrow == 0
    warnings = aml.event_log[aml.event_log['level'] == 'Warn','message']
    last_warning = warnings[warnings.nrow-1,:].flatten()
    assert "Empty leaderboard" in last_warning


def test_leaderboard_for_binomial():
    print("Check leaderboard for Binomial with default sorting")
    ds = prepare_data('binomial')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_binom_sort",
                    seed=automl_seed,
                    max_models=8,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"], "auc", True)


def test_leaderboard_for_multinomial():
    print("Check leaderboard for Multinomial with default sorting")
    ds = prepare_data('multinomial')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_multinom_sort",
                    seed=automl_seed,
                    max_models=8,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_per_class_error", "logloss", "rmse", "mse"], "mean_per_class_error")


def test_leaderboard_for_regression():
    print("Check leaderboard for Regression with default sorting")
    ds = prepare_data('regression')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_regr_sort",
                    exclude_algos=exclude_algos,
                    max_models=8,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "mean_residual_deviance")


def test_leaderboard_with_all_algos():
    print("Check leaderboard for all algorithms")
    ds = prepare_data('multinomial')
    aml = H2OAutoML(project_name="py_aml_lb_test_all_algos",
                    seed=automl_seed,
                    max_models=12)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, [], ["mean_per_class_error", "logloss", "rmse", "mse"], "mean_per_class_error")


def test_leaderboard_with_no_algos():
    print("Check leaderboard for excluding all algos (empty leaderboard)")
    ds = prepare_data('binomial')
    exclude_algos = all_algos
    aml = H2OAutoML(project_name="py_aml_lb_test_no_algo",
                    seed=automl_seed,
                    max_runtime_secs=10,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    lb = aml.leaderboard
    assert lb.nrows == 0
    check_leaderboard(aml, exclude_algos, [], None, None)


def test_leaderboard_for_binomial_with_custom_sorting():
    print("Check leaderboard for Binomial sort by logloss")
    ds = prepare_data('binomial')
    exclude_algos = ["GLM", "DeepLearning", "DRF"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_binom_sort",
                    seed=automl_seed,
                    max_models=10,
                    exclude_algos=exclude_algos,
                    sort_metric="logloss")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["logloss", "auc", "aucpr", "mean_per_class_error", "rmse", "mse"], "logloss")


def test_leaderboard_for_multinomial_with_custom_sorting():
    print("Check leaderboard for Multinomial sort by logloss")
    ds = prepare_data('multinomial')
    exclude_algos = ["DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_multinom_sort",
                    seed=automl_seed,
                    max_models=10,
                    exclude_algos=exclude_algos,
                    sort_metric="logloss")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["logloss", "mean_per_class_error", "rmse", "mse"], "logloss")


def test_leaderboard_for_regression_with_custom_sorting():
    print("Check leaderboard for Regression sort by rmse")
    ds = prepare_data('regression')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_regr_sort",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    seed=automl_seed,
                    sort_metric="RMSE")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["rmse", "mean_residual_deviance", "mse", "mae", "rmsle"], "rmse")


def test_leaderboard_for_regression_with_custom_sorting_deviance():
    print("Check leaderboard for Regression sort by deviance")
    ds = prepare_data('regression')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_regr_deviance",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    seed=automl_seed,
                    sort_metric="deviance")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "rmse")


def test_AUTO_stopping_metric_with_no_sorting_metric_binomial():
    print("Check leaderboard with AUTO stopping metric and no sorting metric for binomial")
    ds = prepare_data('binomial')
    exclude_algos = ["DeepLearning", "GLM", "StackedEnsemble"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_no_sorting_binomial",
                    seed=automl_seed,
                    max_models=12,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"], "auc", True)
    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    first = [m for m in non_se if 'XGBoost_1' in m]
    others = [m for m in non_se if m not in first]
    check_model_property(first, 'stopping_metric', True, None) #if stopping_rounds == 0, actual value of stopping_metric is set to None
    check_model_property(others, 'stopping_metric', True, "logloss")


def test_AUTO_stopping_metric_with_no_sorting_metric_regression():
    print("Check leaderboard with AUTO stopping metric and no sorting metric for regression")
    ds = prepare_data('regression')
    exclude_algos = ["DeepLearning", "GLM"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_no_sorting_regression",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "mean_residual_deviance")
    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    first = [m for m in non_se if 'XGBoost_1' in m]
    others = [m for m in non_se if m not in first]
    check_model_property(first, 'stopping_metric', True, None) #if stopping_rounds == 0, actual value of stopping_metric is set to None
    check_model_property(others, 'stopping_metric', True, "deviance")


def test_AUTO_stopping_metric_with_auc_sorting_metric():
    print("Check leaderboard with AUTO stopping metric and auc sorting metric")
    ds = prepare_data('binomial')
    exclude_algos = ["DeepLearning", "GLM", "StackedEnsemble"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_auc_sorting",
                    seed=automl_seed,
                    max_models=10,
                    exclude_algos=exclude_algos,
                    sort_metric='auc')
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"], "auc", True)
    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    check_model_property(non_se, 'stopping_metric', True, "logloss")


def test_AUTO_stopping_metric_with_custom_sorting_metric():
    print("Check leaderboard with AUTO stopping metric and rmse sorting metric")
    ds = prepare_data('regression')
    exclude_algos = ["DeepLearning", "GLM"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_custom_sorting",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    seed=automl_seed,
                    sort_metric="rmse")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["rmse", "mean_residual_deviance", "mse", "mae", "rmsle"], "rmse")
    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    check_model_property(non_se, 'stopping_metric', True, "RMSE")


def test_custom_leaderboard():
    print("Check custom leaderboard")
    ds = prepare_data('binomial')
    aml = H2OAutoML(project_name="py_aml_custom_lb_test",
                    max_models=5,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)
    std_columns = ["model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"]
    assert aml.leaderboard.names == std_columns
    assert get_leaderboard(aml).names == std_columns
    assert get_leaderboard(aml, extra_columns=[]).names == std_columns
    assert get_leaderboard(aml, extra_columns='ALL').names == std_columns + ["training_time_ms", "predict_time_per_row_ms", "algo"]
    assert get_leaderboard(aml, extra_columns="unknown").names == std_columns
    assert get_leaderboard(aml, extra_columns=["training_time_ms"]).names == std_columns + ["training_time_ms"]
    assert get_leaderboard(aml, extra_columns=["predict_time_per_row_ms", "training_time_ms"]).names == std_columns + ["predict_time_per_row_ms", "training_time_ms"]
    assert get_leaderboard(aml, extra_columns=["unknown", "training_time_ms"]).names == std_columns + ["training_time_ms"]
    lb_ext = get_leaderboard(aml, extra_columns='ALL')
    print(lb_ext)
    assert all(lb_ext[:, [c for c in lb_ext.columns if c not in ("model_id", "algo")]].isnumeric()), "metrics and extension columns should all be numeric"
    assert (lb_ext["training_time_ms"].as_data_frame().values >= 0).all()
    assert (lb_ext["predict_time_per_row_ms"].as_data_frame().values > 0).all()
    assert (lb_ext["algo"].as_data_frame().isin(["DRF", "DeepLearning", "GBM",
                                                 "GLM", "StackedEnsemble", "XGBoost"]).all().all())


def test_custom_leaderboard_as_method():
    ds = prepare_data('binomial')
    aml = H2OAutoML(project_name="py_aml_custom_lb_method_test",
                    max_models=5,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)
    
    assert_frame_equal(aml.get_leaderboard().as_data_frame(), aml.leaderboard.as_data_frame())
    lb_ext = get_leaderboard(aml, extra_columns='ALL')
    assert_frame_equal(aml.get_leaderboard('ALL').as_data_frame(), lb_ext.as_data_frame())
    
    aml2 = get_automl(aml.project_name)
    assert_frame_equal(aml2.get_leaderboard().as_data_frame(), aml.leaderboard.as_data_frame())
    assert_frame_equal(aml2.get_leaderboard('ALL').as_data_frame(), lb_ext.as_data_frame())
    

def test_get_best_model_per_family():
    ds = prepare_data('binomial')
    aml = H2OAutoML(project_name="py_aml_best_model_per_family_test",
                    max_models=12,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    def _check_best_models(model_ids, criterion):
        # test case insensitivity in algo specification
        top_models = [aml.get_best_model(mtype, criterion) for mtype in ["deeplearning", "drf", "gbm", "GLM",
                                                                         "STaCKeDEnsEmblE", "xgboost"]]
        nones = [v is None for v in top_models]
        assert sum(nones) <= 1 and len(nones) >= 6
        seen = set()
        top_model_ids = [m.model_id for m in top_models if m is not None]
        for model_id in model_ids:
            model_type = model_id.split("_")[0]
            if model_type not in seen:
                assert model_id in top_model_ids, "%s not found in top models %s" % (model_id, top_model_ids)
                if model_type in ("DRF", "XRT"):
                    seen.update(["DRF", "XRT"])
                else:
                    seen.add(model_type)
    # Check default criterion
    model_ids = aml.leaderboard.as_data_frame()["model_id"]
    _check_best_models(model_ids, None)

    # Check AUC criterion (the higher the better) and check case insensitivity
    model_ids = aml.leaderboard.sort(by="auc", ascending=False).as_data_frame()["model_id"]
    _check_best_models(model_ids, "AUC")

    # Check it works for custom criterion (MSE)
    model_ids = aml.leaderboard.sort(by="mse").as_data_frame()["model_id"]
    _check_best_models(model_ids, "mse")

    # Check it works for without specifying a model type
    assert aml.get_best_model().model_id == aml.leaderboard[0, "model_id"]

    # Check it works with just criterion
    assert aml.get_best_model(criterion="mse").model_id == aml.leaderboard.sort(by="mse")[0, "model_id"]

    # Check it works with extra_cols
    top_model = h2o.automl.get_leaderboard(aml, extra_columns=["training_time_ms"]).sort(by="training_time_ms")[0, "model_id"]
    assert aml.get_best_model(criterion="training_time_ms").model_id == top_model

    # Check validation works
    try:
        aml.get_best_model(algorithm="GXboost")
        assert False, "Algorithm validation does not work!"
    except H2OValueError:
        pass
    try:
        aml.get_best_model(criterion="lorem_ipsum_dolor_sit_amet")
        assert False, "Criterion validation does not work!"
    except H2OValueError:
        pass


pyunit_utils.run_tests([
    test_warn_on_empty_leaderboard,
    test_leaderboard_for_binomial,
    test_leaderboard_for_multinomial,
    test_leaderboard_for_regression,
    test_leaderboard_with_all_algos,
    test_leaderboard_with_no_algos,
    test_leaderboard_for_binomial_with_custom_sorting,
    test_leaderboard_for_multinomial_with_custom_sorting,
    test_leaderboard_for_regression_with_custom_sorting,
    test_AUTO_stopping_metric_with_no_sorting_metric_binomial,
    test_AUTO_stopping_metric_with_no_sorting_metric_regression,
    test_AUTO_stopping_metric_with_auc_sorting_metric,
    test_AUTO_stopping_metric_with_custom_sorting_metric,
    test_custom_leaderboard,
    test_custom_leaderboard_as_method,
    test_get_best_model_per_family,
])
