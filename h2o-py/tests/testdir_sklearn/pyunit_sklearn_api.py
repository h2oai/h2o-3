from __future__ import print_function
import importlib, inspect, os, sys

sys.path.insert(1, os.path.join("..",".."))
from tests import pyunit_utils

sklearn_estimator_methods = ['fit', 'predict', 'predict_proba', 'predict_log_proba', 'score',
                             'get_params', 'set_params']

sklearn_transformer_methods = ['fit', 'fit_transform', 'transform', 'inverse_transform',
                               'get_params', 'set_params']


def _make_estimator_names(cls_name):
    mod = importlib.import_module('h2o.sklearn')
    suffixes = ['Estimator', 'Classifier', 'Regressor']
    if cls_name in mod._excluded_estimators:
        return []
    elif cls_name in mod._generic_only_estimators:
        suffixes = ['Estimator']
    elif cls_name in mod._classifier_only_estimators:
        suffixes = ['Estimator', 'Classifier']
    elif cls_name in mod._regressor_only_estimators:
        suffixes = ['Estimator', 'Regressor']
    return map(lambda suffix: cls_name.replace('Estimator', '')+suffix,
               suffixes)

def _make_transformer_names(cls_name):
    mod = importlib.import_module('h2o.sklearn')
    if cls_name in mod._excluded_estimators:
        return []
    return [cls_name]


def _has_method(cls, name):
    return hasattr(cls, name) and callable(getattr(cls, name))


def _check_exposed_in_h2o_sklearn_module(cls):
    mod = importlib.import_module('h2o.sklearn')
    cls_in_mod = getattr(mod, cls.__name__, None)
    assert cls_in_mod == cls


def test_automl_estimators_exposed_in_h2o_sklearn_automl_module():
    mod = importlib.import_module('h2o.sklearn.automl')
    for name in _make_estimator_names('H2OAutoML'):
        cls = getattr(mod, name, None)
        assert cls, "Class {} is missing in module {}".format(name, mod)
        for meth in sklearn_estimator_methods:
            assert _has_method(cls, meth), "Class {} is missing method {}".format(name, meth)
        _check_exposed_in_h2o_sklearn_module(cls)


def test_algos_estimators_exposed_in_h2o_sklearn_estimators_module():
    import h2o.estimators
    mod = importlib.import_module('h2o.sklearn.estimators')
    class_names = [name for name, _ in inspect.getmembers(h2o.estimators, inspect.isclass)]
    for cl_name in class_names:
        for name in _make_estimator_names(cl_name):
            cls = getattr(mod, name, None)
            assert cls, "Class {} is missing in module {}".format(name, mod)
            for meth in sklearn_estimator_methods:
                assert _has_method(cls, meth), "Class {} is missing method {}".format(name, meth)
            _check_exposed_in_h2o_sklearn_module(cls)


def test_transformers_exposed_in_h2o_sklean_transforms_module():
    import h2o.transforms
    mod = importlib.import_module('h2o.sklearn.transforms')
    class_names = [name for name, _ in inspect.getmembers(h2o.transforms, inspect.isclass)]
    for cl_name in class_names:
        for name in _make_transformer_names(cl_name):
            cls = getattr(mod, name, None)
            assert cls, "Class {} is missing in module {}".format(name, mod)
            for meth in sklearn_transformer_methods:
                assert _has_method(cls, meth), "Class {} is missing method {}".format(name, meth)
            _check_exposed_in_h2o_sklearn_module(cls)


pyunit_utils.run_tests([
    test_automl_estimators_exposed_in_h2o_sklearn_automl_module,
    test_algos_estimators_exposed_in_h2o_sklearn_estimators_module,
    test_transformers_exposed_in_h2o_sklean_transforms_module,
])
