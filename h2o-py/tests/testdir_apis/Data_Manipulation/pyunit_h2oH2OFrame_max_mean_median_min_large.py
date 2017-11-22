from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np
from h2o.frame import H2OFrame


def h2o_H2OFrame_stats():
    """
    Python API test: h2o.frame.H2OFrame.max(), h2o.frame.H2OFrame.mean(), h2o.frame.H2OFrame.median(),
    h2o.frame.H2OFrame.min(),
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    assert abs(h2oframe.max()-np.ndarray.max(python_lists)) < 1e-12, "h2o.H2OFrame.max() command is not working."
    assert abs(h2oframe.min()-np.ndarray.min(python_lists)) < 1e-12, "h2o.H2OFrame.min() command is not working."

    h2oMean = h2oframe.mean(skipna=False, axis=0)
    assert_is_type(h2oMean, H2OFrame)
    numpmean = list(np.mean(python_lists, axis=0))
    h2omean = h2oMean.as_data_frame(use_pandas=True, header=False)
    assert pyunit_utils.equal_two_arrays(numpmean, h2omean.values.tolist()[0], 1e-12, 1e-6), "h2o.H2OFrame.mean() command is not working."

    h2oMedian = h2oframe.median(na_rm=True)
    assert_is_type(h2oMedian, list)
    numpmedian = list(np.median(python_lists, axis=0))
    assert pyunit_utils.equal_two_arrays(numpmedian, h2oMedian, 1e-12, 1e-6), "h2o.H2OFrame.median() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_stats())
else:
    h2o_H2OFrame_stats()
