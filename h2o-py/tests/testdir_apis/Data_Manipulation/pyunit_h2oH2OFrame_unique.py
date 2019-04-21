from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
import numpy as np
from h2o.frame import H2OFrame


def h2o_H2OFrame_unique():
    """
    Python API test: h2o.frame.H2OFrame.unique()
    """
    python_lists = np.random.randint(-5,5, (100, 1))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newFrame = h2oframe.unique()
    allLevels = h2oframe.asfactor().levels()[0]
    assert_is_type(newFrame, H2OFrame)     # check return type
    assert len(allLevels)==newFrame.nrow, "h2o.H2OFrame.unique command is not working." # check shape
    newFrame = newFrame.asfactor()    # change to enum to make sure elements are string type

    for rowIndex in range(newFrame.nrow):   # check values
        assert newFrame[rowIndex, 0] in allLevels, "h2o.H2OFrame.unique command is not working." # check shape


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_unique())
else:
    h2o_H2OFrame_unique()
