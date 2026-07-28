import importlib, inspect, os, sys
from sklearn.base import clone, is_classifier, is_regressor

sys.path.insert(1, os.path.join("..",".."))
from tests import pyunit_utils

sklearn_estimator_methods = ['fit', 'predict', 'fit_predict', 'get_params', 'set_params']

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
        for meth in sklearn_estimator_methods + ['score']:
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
            if name.endswith('Classifier') or name.endswith('Regressor'):
                meth = 'score'
                assert _has_method(cls, meth), "Class {} is missing method {}".format(name, meth)
                for meth in ['predict_proba', 'predict_log_proba']:
                    if name.endswith('Classifier'):
                        assert _has_method(cls, meth), "Class {} is missing method {}".format(name, meth)
                    else:
                        assert not _has_method(cls, meth), "Class {} should not have method {}".format(name, meth)
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


def test_classifier_and_regressor_identification():
    mod = importlib.import_module('h2o.sklearn')
    clf = mod.H2OGradientBoostingClassifier()
    reg = mod.H2OGradientBoostingRegressor()
    generic_as_clf = mod.H2OGradientBoostingEstimator(estimator_type='classifier')
    generic_as_reg = mod.H2OGradientBoostingEstimator(estimator_type='regressor')

    assert is_classifier(clf), "{} should be recognized as classifier".format(clf.__class__.__name__)
    assert not is_regressor(clf), "{} should not be recognized as regressor".format(clf.__class__.__name__)
    assert is_regressor(reg), "{} should be recognized as regressor".format(reg.__class__.__name__)
    assert not is_classifier(reg), "{} should not be recognized as classifier".format(reg.__class__.__name__)
    assert is_classifier(generic_as_clf), "{} should be classifier when estimator_type is set".format(generic_as_clf.__class__.__name__)
    assert is_regressor(generic_as_reg), "{} should be regressor when estimator_type is set".format(generic_as_reg.__class__.__name__)


def test_clone_preserves_estimator_type_semantics():
    mod = importlib.import_module('h2o.sklearn')
    original = mod.H2OGradientBoostingEstimator(estimator_type='classifier')
    cloned = clone(original)
    assert is_classifier(cloned), "{} clone should preserve classifier semantics".format(cloned.__class__.__name__)
    assert not is_regressor(cloned), "{} clone should not become a regressor".format(cloned.__class__.__name__)


def test_sklearn_tags_estimator_type_when_available():
    mod = importlib.import_module('h2o.sklearn')
    generic_as_clf = mod.H2OGradientBoostingEstimator(estimator_type='classifier')
    generic_as_reg = mod.H2OGradientBoostingEstimator(estimator_type='regressor')

    for est, expected in [(generic_as_clf, 'classifier'), (generic_as_reg, 'regressor')]:
        if not hasattr(est, '__sklearn_tags__'):
            continue
        tags = est.__sklearn_tags__()
        if isinstance(tags, dict):
            continue  # older sklearn API
        if hasattr(tags, 'estimator_type'):
            assert tags.estimator_type == expected, \
                "Unexpected estimator_type tag for {}: {}".format(est.__class__.__name__, tags.estimator_type)
        if hasattr(tags, 'target_tags') and hasattr(tags.target_tags, 'required'):
            assert tags.target_tags.required is True


pyunit_utils.run_tests([
    test_automl_estimators_exposed_in_h2o_sklearn_automl_module,
    test_algos_estimators_exposed_in_h2o_sklearn_estimators_module,
    test_transformers_exposed_in_h2o_sklean_transforms_module,
    test_classifier_and_regressor_identification,
    test_clone_preserves_estimator_type_semantics,
    test_sklearn_tags_estimator_type_when_available,
])
