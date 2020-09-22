from __future__ import print_function
import sys, os, collections

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
import matplotlib.pyplot
from tests import pyunit_utils
from h2o.automl import H2OAutoML
from h2o.estimators import H2OGradientBoostingEstimator


def test_mli_single_model_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234)
    gbm.train(y=y, training_frame=train)

    # test shap summary
    assert isinstance(gbm.shap_summary_plot(train), matplotlib.pyplot.Figure)

    # test shap explain row
    assert isinstance(gbm.shap_explain_row(train, 1), matplotlib.pyplot.Figure)

    # test residual analysis
    assert isinstance(gbm.residual_analysis(train), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.individual_conditional_expectations(train, col), matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(gbm.explain(train, render=False), collections.OrderedDict)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), collections.OrderedDict)


def test_mli_automl_model_regression():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/wine/winequality-redwhite-no-BOM.csv"))
    y = "quality"
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    # test variable importance heatmap plot
    assert isinstance(aml.variable_importance_heatmap(), matplotlib.pyplot.Figure)

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train), matplotlib.pyplot.Figure)

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.partial_dependences(train, col), matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(aml.explain(train, render=False), collections.OrderedDict)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), collections.OrderedDict)


def test_mli_single_model_binomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234)
    gbm.train(y=y, training_frame=train)

    # test shap summary
    assert isinstance(gbm.shap_summary_plot(train), matplotlib.pyplot.Figure)

    # test shap explain row
    assert isinstance(gbm.shap_explain_row(train, 1), matplotlib.pyplot.Figure)

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.individual_conditional_expectations(train, col), matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(gbm.explain(train, render=False), collections.OrderedDict)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), collections.OrderedDict)


def test_mli_automl_model_binomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    y = "CAPSULE"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    # test variable importance heatmap plot
    assert isinstance(aml.variable_importance_heatmap(), matplotlib.pyplot.Figure)

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train), matplotlib.pyplot.Figure)

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.partial_dependences(train, col), matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(aml.explain(train, render=False), collections.OrderedDict)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), collections.OrderedDict)


def test_mli_single_model_multinomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris2.csv"))
    y = "response"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234)
    gbm.train(y=y, training_frame=train)

    # test shap summary
    try:
        gbm.shap_summary_plot(train)
        assert False, "SHAP Contributions aren't implemented for multinomial classification => should fail"
    except EnvironmentError:
        pass

    # test shap explain row
    try:
        gbm.shap_explain_row(train, 1)
        assert False, "SHAP Contributions aren't implemented for multinomial classification => should fail"
    except EnvironmentError:
        pass

    # test ICE plot
    for col in cols_to_test:
        assert isinstance(gbm.individual_conditional_expectations(train, col, target="setosa"),
                          matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(gbm.explain(train, render=False), collections.OrderedDict)

    # test explain row
    assert isinstance(gbm.explain_row(train, 1, render=False), collections.OrderedDict)


def test_mli_automl_model_multinomial_classification():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris2.csv"))
    y = "response"
    train[y] = train[y].asfactor()
    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    aml = H2OAutoML(seed=1234, max_models=5)
    aml.train(y=y, training_frame=train)

    # test variable importance heatmap plot
    assert isinstance(aml.variable_importance_heatmap(), matplotlib.pyplot.Figure)

    # test model correlation heatmap plot
    assert isinstance(aml.model_correlation_heatmap(train), matplotlib.pyplot.Figure)

    # test partial dependences
    for col in cols_to_test:
        assert isinstance(aml.partial_dependences(train, col, target="setosa"), matplotlib.pyplot.Figure)

    # test explain
    assert isinstance(aml.explain(train, render=False), collections.OrderedDict)

    # test explain row
    assert isinstance(aml.explain_row(train, 1, render=False), collections.OrderedDict)


pyunit_utils.run_tests([
    test_mli_single_model_regression,
    test_mli_automl_model_regression,
    test_mli_single_model_binomial_classification,
    test_mli_automl_model_binomial_classification,
    test_mli_single_model_multinomial_classification,
    test_mli_automl_model_multinomial_classification,
    ])
