from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
import random
import sys
from tests import pyunit_utils
from h2o.automl import H2OAutoML

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

    j_leaderboard = aml._get_params()['leaderboard']
    sort_metric = j_leaderboard['sort_metric']
    assert sort_metric == expected_sort_metric, \
        "expected leaderboard sorted by {expected} but was sorted by {actual}".format(expected=expected_sort_metric, actual=sort_metric)
    sorted_desc = j_leaderboard['sort_decreasing']
    assert sorted_desc == expected_sorted_desc, \
        "expected leaderboard sorted {expected} but was sorted {actual}".format(expected="desc" if expected_sorted_desc else "asc",
                                                                                actual="desc" if sorted_desc else "asc")


def test_leaderboard_for_binomial():
    print("Check leaderboard for Binomial with default sorting")
    ds = prepare_data('binomial')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_binom_sort",
                    seed=automl_seed,
                    max_models=5,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "mean_per_class_error", "rmse", "mse"], "auc", True)


def test_leaderboard_for_multinomial():
    print("Check leaderboard for Multinomial with default sorting")
    ds = prepare_data('multinomial')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_multinom_sort",
                    seed=automl_seed,
                    max_models=5,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_per_class_error", "logloss", "rmse", "mse"], "mean_per_class_error")


def test_leaderboard_for_regression():
    print("Check leaderboard for Regression with default sorting")
    ds = prepare_data('regression')
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_regr_sort",
                    exclude_algos=exclude_algos,
                    max_models=5,
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
    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "mean_per_class_error", "rmse", "mse"], None)


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

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "mean_per_class_error", "rmse", "mse"], "logloss")


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

    check_leaderboard(aml, exclude_algos, ["mean_per_class_error", "logloss", "rmse", "mse"], "logloss")


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

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "rmse")


def test_AUTO_stopping_metric_with_no_sorting_metric_binomial():
    print("Check leaderboard with AUTO stopping metric and no sorting metric for binomial")
    ds = prepare_data('binomial')
    exclude_algos = ["DeepLearning", "GLM", "StackedEnsemble"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_no_sorting_binomial",
                    seed=automl_seed,
                    max_models=10,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "mean_per_class_error", "rmse", "mse"], "auc", True)
    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    first = [m for m in non_se if 'DRF' in m]
    others = [m for m in non_se if m not in first]
    check_model_property(first, 'stopping_metric', True, "AUTO")
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
    first = [m for m in non_se if 'DRF' in m]
    others = [m for m in non_se if m not in first]
    check_model_property(first, 'stopping_metric', True, "AUTO")
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

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "mean_per_class_error", "rmse", "mse"], "auc", True)
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

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "rmse")
    non_se = get_partitioned_model_names(aml.leaderboard).non_se
    check_model_property(non_se, 'stopping_metric', True, "RMSE")


tests = [
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
    test_AUTO_stopping_metric_with_custom_sorting_metric
]

if __name__ == "__main__":
    for test in tests: pyunit_utils.standalone_test(test)
else:
    for test in tests: test()
