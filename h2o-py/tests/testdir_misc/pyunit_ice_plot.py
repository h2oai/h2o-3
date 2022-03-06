from __future__ import print_function

import sys
import os

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")  # remove warning from python2 (missing TKinter)
import h2o
import matplotlib.pyplot
from tests import pyunit_utils
from h2o.estimators import *
from h2o.explanation._explain import *
from h2o.explanation._explain import _handle_orig_values


def test_original_values():
    paths = ["smalldata/titanic/titanic_expanded.csv", "smalldata/logreg/prostate.csv", "smalldata/iris/iris2.csv"]
    ys = ["fare", "CAPSULE", "response"]
    names_to_extract = ["name", None, None]
    targets = [None, None, "setosa"]

    for i in range(len(paths)):
        train = h2o.upload_file(pyunit_utils.locate(paths[i]))
        gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model_py" + str(i))
        gbm.train(y=ys[i], training_frame=train)

        cols_to_test = _get_cols_to_test(train, ys[i])
        if names_to_extract[i] is not None:
            if names_to_extract[i] in cols_to_test: cols_to_test.remove(names_to_extract[i])

        _assert_pyplot_was_produced(cols_to_test, gbm, train, target=targets[i])


def test_handle_orig_values():
    type_test = ["Regression", "Binomial", "Multinomial"]
    paths = ["smalldata/titanic/titanic_expanded.csv",  "smalldata/logreg/prostate.csv", "smalldata/iris/iris2.csv"]
    ys = ["fare", "CAPSULE", "response"]
    names_to_extract = ["name", None, None]
    targets = [None, None, "setosa"]
    colormap = "plasma"

    for test_id in range(len(paths)):
        train = h2o.upload_file(pyunit_utils.locate(paths[test_id]))
        if type_test[test_id] in ["Binomial", "Multinomial"]:
            train[ys[test_id]] = train[ys[test_id]].asfactor()

        gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model_py" + str(test_id))
        gbm.train(y=ys[test_id], training_frame=train)

        cols_to_test = _get_cols_to_test(train, ys[test_id])
        print(cols_to_test)
        if names_to_extract[test_id] is not None:
            if names_to_extract[test_id] in cols_to_test: cols_to_test.remove(names_to_extract[test_id])

        plt = get_matplotlib_pyplot(False, raise_if_not_available=True)
        frame = train.sort(gbm.actual_params["response_column"])
        for column in cols_to_test:
            is_factor = frame[column].isfactor()[0]
            deciles = [int(round(frame.nrow * dec / 10)) for dec in range(11)]
            deciles[10] = frame.nrow - 1
            colors = plt.get_cmap(colormap, 11)(list(range(11)))
            for i, index in enumerate(deciles):
                percentile_string = "{}th Percentile".format(i * 10)
                if targets[test_id] is not None:
                    target = [targets[test_id]]
                else:
                    target = None
                tmp = NumpyFrame(
                    gbm.partial_plot(
                        frame,
                        cols=[column],
                        plot=False,
                        row_index=index,
                        targets=target,
                        nbins=100 if not is_factor else 1 + frame[column].nlevels()[0],
                        include_na=True
                    )[0]
                )
                encoded_col = tmp.columns[0]
                orig_value_prediction = _handle_orig_values(is_factor, tmp, encoded_col, plt, target, gbm,
                                                            frame, index, column, colors[i], percentile_string)

                if type_test[test_id] == "Regression":
                    assert gbm.training_model_metrics()["model_category"] == "Regression"
                    np.testing.assert_almost_equal(orig_value_prediction["mean_response"], gbm.predict(frame).as_data_frame()["predict"][index], 5)
                elif type_test[test_id] == "Binomial":
                    assert gbm.training_model_metrics()["model_category"] == "Binomial"
                    np.testing.assert_almost_equal(orig_value_prediction["mean_response"], gbm.predict(frame).as_data_frame()["p1"][index], 5)
                elif type_test[test_id] == "Multinomial":
                    assert gbm.training_model_metrics()["model_category"] == "Multinomial"
                    np.testing.assert_almost_equal(orig_value_prediction["mean_response"], gbm.predict(frame).as_data_frame()[targets[test_id]][index], 5)


def _get_cols_to_test(train, y):
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)
    return cols_to_test


def _assert_pyplot_was_produced(cols_to_test, model, train, target=None):
    for col in cols_to_test:
        if target is None:
            assert isinstance(model.ice_plot(train, col).figure(), matplotlib.pyplot.Figure)
        else:
            assert isinstance(model.ice_plot(train, col, target=target).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")


def test_display_mode():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "fare"

    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    assert isinstance(gbm.ice_plot(train, 'title').figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'title', show_pdp=True).figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'title', show_pdp=False).figure(), matplotlib.pyplot.Figure)

    assert isinstance(gbm.ice_plot(train, 'age').figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age', show_pdp=True).figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age', show_pdp=False).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")


def test_binary_response_scale():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "survived"

    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    assert isinstance(gbm.ice_plot(train, 'title', binary_response_scale="logodds").figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age').figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")

    try:
        gbm.ice_plot(train, 'title', binary_response_scale="invalid_value")
    except ValueError as e:
        assert str(e) == "Unsupported value for binary_response_scale!"

    y = "fare"
    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    try:
        gbm.ice_plot(train, 'title', binary_response_scale="logodds")
    except ValueError as e:
        assert str(e) == "binary_response_scale cannot be set to 'logodds' value for non-binomial models!"


def test_show_pdd():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    y = "fare"

    # get at most one column from each type
    cols_to_test = []
    for col, typ in train.types.items():
        for ctt in cols_to_test:
            if typ == train.types[ctt] or col == y:
                break
        else:
            cols_to_test.append(col)

    gbm = H2OGradientBoostingEstimator(seed=1234, model_id="my_awesome_model")
    gbm.train(y=y, training_frame=train)

    assert isinstance(gbm.ice_plot(train, 'title').figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'title', show_pdp=True).figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'title', show_pdp=False).figure(), matplotlib.pyplot.Figure)

    assert isinstance(gbm.ice_plot(train, 'age').figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age', show_pdp=True).figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age', show_pdp=False).figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")



pyunit_utils.run_tests([
    test_original_values,
    test_handle_orig_values,
    test_display_mode,
    test_binary_response_scale,
    test_show_pdd
])
