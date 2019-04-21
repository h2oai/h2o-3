from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


def h2o_H2OFrame_categories():
    """
    Python API test: h2o.frame.H2OFrame.categories()
    """
    python_lists = np.random.randint(4, size=(10,1))
    h2oframe = h2o.H2OFrame(python_obj=python_lists, column_types=['enum'])
    alllevels = h2oframe.categories()
    alllevels = [int(i) for i in alllevels]     # convert string into integers for comparison
    truelevels = np.unique(python_lists).tolist()   # categorical levels calculated from Python
    assert alllevels==truelevels, "h2o.H2OFrame.categories() command is not working."
    assert pyunit_utils.equal_two_arrays(alllevels, truelevels, 1e-10, 0), "h2o.H2OFrame.categories() command is" \
                                                                           " not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_categories())
else:
    h2o_H2OFrame_categories()
