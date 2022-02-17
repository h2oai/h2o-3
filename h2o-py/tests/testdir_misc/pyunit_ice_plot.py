from __future__ import print_function

import os
import sys

sys.path.insert(1,"../../")
import matplotlib
matplotlib.use("Agg")  # remove warning from python2 (missing TKinter)
import h2o
import matplotlib.pyplot
from tests import pyunit_utils
from h2o.estimators import *

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
    test_binary_response_scale,
    test_show_pdd
])