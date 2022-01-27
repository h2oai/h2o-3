from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..", "..", ".."))
import matplotlib
matplotlib.use("Agg")  # remove warning from python2 (missing TKinter)
import h2o
import matplotlib.pyplot
from tests import pyunit_utils
from h2o.estimators import *


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
    assert isinstance(gbm.ice_plot(train, 'title', display_mode="ice").figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'title', display_mode="pdp").figure(), matplotlib.pyplot.Figure)

    assert isinstance(gbm.ice_plot(train, 'age').figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age', display_mode="ice").figure(), matplotlib.pyplot.Figure)
    assert isinstance(gbm.ice_plot(train, 'age', display_mode="pdp").figure(), matplotlib.pyplot.Figure)
    matplotlib.pyplot.close("all")



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_display_mode)
else:
    test_display_mode()
