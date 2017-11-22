from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
from random import randrange
import numpy as np

def h2o_H2OFrame_sum():
    """
    Python API test: h2o.frame.H2OFrame.meansum(skipna=True, axis=0, **kwargs)
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)

    # axis = 0
    h2oSum = h2oframe.sum(skipna=False, axis=0)
    assert_is_type(h2oSum, H2OFrame)
    numpsum = list(np.sum(python_lists, axis=0))
    h2omean = h2oSum.as_data_frame(use_pandas=True, header=False)
    assert pyunit_utils.equal_two_arrays(numpsum, h2omean.values.tolist()[0], 1e-12, 1e-6), "h2o.H2OFrame.meansum()" \
                                                                                            " command is not working."

    # axis = 1
    h2oSum = h2oframe.sum(skipna=False, axis=1)
    assert_is_type(h2oSum, H2OFrame)
    numpsum = list(np.sum(python_lists, axis=1))
    h2omean = h2oSum.as_data_frame(use_pandas=True, header=False)
    assert pyunit_utils.equal_two_arrays(numpsum, h2omean.values.T.tolist()[0], 1e-12, 1e-6), "h2o.H2OFrame.meansum()" \
                                                                                              " command is not working."
if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_sum())
else:
    h2o_H2OFrame_sum()
