from __future__ import print_function
import sys
from collections import OrderedDict

sys.path.insert(1,"../../../")
import re
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def list_keys_in_memory():
    mem_keys = h2o.ls().key
    gbm_keys = [k for k in mem_keys if re.search(r'^Grid_GBM_.*_model_\d+(_|$)', k)]
    pred_keys = [k for k in mem_keys if re.search(r'(^|_)prediction_', k)]
    metrics_keys = [k for k in mem_keys if re.search(r'^modelmetrics_', k)]
    model_keys = [k for k in gbm_keys if k not in pred_keys and k not in metrics_keys]
    cv_keys = [k for k in mem_keys if re.search(r'(^|_)cv_', k)]
    cv_pred_keys = [k for k in cv_keys if k in pred_keys]
    cv_metrics_keys = [k for k in cv_keys if k in metrics_keys]
    cv_mod_keys = [k for k in cv_keys if k in model_keys]
    return dict(
        all=mem_keys,
        models=model_keys,
        predictions=pred_keys,
        metrics=metrics_keys,
        gbm=gbm_keys,
        cv_all=cv_keys,
        cv_models=cv_mod_keys,
        cv_predictions=cv_pred_keys,
        cv_metrics=cv_metrics_keys
    )


def prepare_data():
    return h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))


def setup_grid():
    h2o.remove_all()
    hyper_parameters = OrderedDict()
    hyper_parameters["learn_rate"] = [0.1, 0.05, 0.01]
    hyper_parameters["ntrees"] = [1, 3, 5]
    gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
    return gs


def test_keep_cross_validation_predictions_on_gbm_grid():
    kcvp = 'keep_cross_validation_predictions'
    nfolds = 5

    def test_defaults():
        print("\n=== "+kcvp+" default behaviour ===")
        grid_search = setup_grid()
        train = prepare_data()
        grid_search.train(x=range(4), y=4, training_frame=train, nfolds=nfolds)
        keys = list_keys_in_memory()
        predictions = len(keys['cv_predictions'])
        assert predictions == 0, "{preds} CV predictions were not cleaned from memory".format(preds=predictions)
        for m in grid_search.models:
            assert not m.cross_validation_predictions(), "unexpected cv predictions for model "+m

    def test_property_enabled():
        print("\n=== enabling "+kcvp+" ===")
        grid_search = setup_grid()
        train = prepare_data()
        grid_search.train(x=range(4), y=4, training_frame=train, nfolds=nfolds,
                          keep_cross_validation_predictions=True)
        keys = list_keys_in_memory()
        predictions = len(keys['cv_predictions'])
        expected = len(grid_search.models) * (nfolds + 1)  # +1 for holdout prediction
        assert predictions == expected, "missing CV predictions in memory, got {actual}, expected {expected}".format(actual=predictions, expected=expected)
        for m in grid_search.models:
            assert m.cross_validation_predictions(), "missing cv predictions for model "+m

    def test_property_disabled():
        print("\n=== disabling "+kcvp+" ===")
        grid_search = setup_grid()
        train = prepare_data()
        grid_search.train(x=range(4), y=4, training_frame=train, nfolds=nfolds,
                          keep_cross_validation_predictions=False)
        keys = list_keys_in_memory()
        predictions = len(keys['cv_predictions'])
        assert predictions == 0, "{preds} CV predictions were not cleaned from memory".format(preds=predictions)
        for m in grid_search.models:
            assert not m.cross_validation_predictions(), "unexpected cv predictions for model "+m


    test_defaults()
    test_property_enabled()
    test_property_disabled()


def test_keep_cross_validation_models_on_gbm_grid():
    kcvm = 'keep_cross_validation_models'
    nfolds = 5

    def test_defaults():
        print("\n=== "+kcvm+" default behaviour ===")
        grid_search = setup_grid()
        train = prepare_data()
        grid_search.train(x=range(4), y=4, training_frame=train, nfolds=nfolds)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models']), len(keys['cv_models'])
        print("total grid models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no grid models left in memory"
        expected = len(grid_search.models) * nfolds
        assert cv == expected, "missing CV models in memory, got {actual}, expected {expected}".format(actual=cv, expected=expected)
        for m in grid_search.models:
            assert m.cross_validation_models(), "missing cv models for model "+m

    def test_property_enabled():
        print("\n=== enabling "+kcvm+" ===")
        grid_search = setup_grid()
        train = prepare_data()
        grid_search.train(x=range(4), y=4, training_frame=train, nfolds=nfolds,
                          keep_cross_validation_models=True)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models']), len(keys['cv_models'])
        print("total grid models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no grid models left in memory"
        expected = len(grid_search.models) * nfolds
        assert cv == expected, "missing CV models in memory, got {actual}, expected {expected}".format(actual=cv, expected=expected)
        for m in grid_search.models:
            assert m.cross_validation_models(), "missing cv models for model "+m

    def test_property_disabled():
        print("\n=== disabling "+kcvm+" ===")
        grid_search = setup_grid()
        train = prepare_data()
        grid_search.train(x=range(4), y=4, training_frame=train, nfolds=nfolds,
                 keep_cross_validation_models=False)
        keys = list_keys_in_memory()
        tot, cv = len(keys['models']), len(keys['cv_models'])
        print("total grid models in memory = {tot}, among which {cv} CV models".format(tot=tot, cv=cv))
        assert tot > 0, "no grid models left in memory"
        assert cv == 0, "{cv} CV models were not cleaned from memory".format(cv=cv)
        for m in grid_search.models:
            assert not m.cross_validation_models(), "unexpected cv models for model "+m


    test_defaults()
    test_property_enabled()
    test_property_disabled()


def test_all():
    test_keep_cross_validation_predictions_on_gbm_grid()
    test_keep_cross_validation_models_on_gbm_grid()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_all)
else:
    test_all()


