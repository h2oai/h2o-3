import sys
import warnings

sys.path.insert(1, "../../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils as pu


def _assert_param_already_set_warning(param, warns):
    messages = [str(w.message) for w in warns if w.category == UserWarning]
    assert any("`%s` parameter has been already set and had a different value in `train` method." % param in m for m in messages)


def _assert_final_param_value(param, value, model):
    if value is None:
        assert model.params[param]['input'] is None
    else:
        assert model.params[param]['input']['column_name'] == value


def test_emit_no_warning_on_param_set_only_on_constructor():
    train = h2o.import_file(pu.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = H2OGradientBoostingEstimator(ntrees=1, offset_column="sulphates", weights_column="alcohol", fold_column=None)
    with warnings.catch_warnings(record=True) as w:
        gbm.train(x=x, y=y, training_frame=train)
    assert len(w) == 0
    _assert_final_param_value('offset_column', "sulphates", gbm)
    _assert_final_param_value('weights_column', "alcohol", gbm)
    _assert_final_param_value('fold_column', None, gbm)


def test_emit_no_warning_on_param_set_only_on_train_method():
    train = h2o.import_file(pu.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = H2OGradientBoostingEstimator(ntrees=1)
    with warnings.catch_warnings(record=True) as w:
        gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")
    assert len(w) == 0
    _assert_final_param_value('offset_column', "sulphates", gbm)
    _assert_final_param_value('weights_column', "alcohol", gbm)
    _assert_final_param_value('fold_column', "type", gbm)


def test_emit_no_warning_on_param_set_same_on_both_constructor_and_train_method():
    train = h2o.import_file(pu.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = H2OGradientBoostingEstimator(ntrees=1, offset_column="sulphates", weights_column="alcohol", fold_column="type")
    with warnings.catch_warnings(record=True) as w:
        gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")
    assert len(w) == 0
    _assert_final_param_value('offset_column', "sulphates", gbm)
    _assert_final_param_value('weights_column', "alcohol", gbm)
    _assert_final_param_value('fold_column', "type", gbm)


def test_emit_no_warning_if_constructor_param_is_explicitly_set_to_default_value():
    train = h2o.import_file(pu.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = H2OGradientBoostingEstimator(ntrees=1, offset_column=None, weights_column=None, fold_column=None)
    with warnings.catch_warnings(record=True) as w:
        gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")
    assert len(w) == 0
    _assert_final_param_value('offset_column', "sulphates", gbm)
    _assert_final_param_value('weights_column', "alcohol", gbm)
    _assert_final_param_value('fold_column', "type", gbm)


def test_emit_warning_on_param_set_differently_on_both_constructor_and_train_method():
    train = h2o.import_file(pu.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = H2OGradientBoostingEstimator(ntrees=1, offset_column="sulph", weights_column="alc", fold_column="tp")
    with warnings.catch_warnings(record=True) as w:
        gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")
    assert len(w) == 3
    _assert_param_already_set_warning('offset_column', w)
    _assert_param_already_set_warning('weights_column', w)
    _assert_param_already_set_warning('fold_column', w)
    _assert_final_param_value('offset_column', "sulphates", gbm)
    _assert_final_param_value('weights_column', "alcohol", gbm)
    _assert_final_param_value('fold_column', "type", gbm)
    

def test_emit_warning_for_each_call_to_train_that_is_overriding_params():
    train = h2o.import_file(pu.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    x = ["citric acid", "residual sugar", "chlorides", "free sulfur dioxide", "total sulfur dioxide", "density", "pH"]
    gbm = H2OGradientBoostingEstimator(ntrees=1, offset_column="sulph", weights_column="alc", fold_column="tp")
    with warnings.catch_warnings(record=True) as w:
        warnings.simplefilter('default')
        gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")
        gbm.train(x=x, y=y, training_frame=train, offset_column="sulphates", weights_column="alcohol", fold_column="type")
    assert len(w) == 2*3


pu.run_tests([
    test_emit_no_warning_on_param_set_only_on_constructor,
    test_emit_no_warning_on_param_set_only_on_train_method,
    test_emit_no_warning_on_param_set_same_on_both_constructor_and_train_method,
    test_emit_no_warning_if_constructor_param_is_explicitly_set_to_default_value,
    test_emit_warning_on_param_set_differently_on_both_constructor_and_train_method,
    test_emit_warning_for_each_call_to_train_that_is_overriding_params,
])

