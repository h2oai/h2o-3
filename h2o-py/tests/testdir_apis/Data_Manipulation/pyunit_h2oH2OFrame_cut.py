from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_cut():
    """
    Python API test: h2o.frame.H2OFrame.cut(breaks, labels=None, include_lowest=False, right=True, dig_lab=3)[source]
    """
    python_lists = np.random.uniform(-2,2, (100,1))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    breaks = [-2, 1, 0, 1, 2]
    newframe = h2oframe.cut(breaks, labels=None, include_lowest=False, right=True, dig_lab=3)
    assert_is_type(newframe, H2OFrame)  # check return type as H2OFrame
    # check returned frame content is correct
    assert newframe.types["C1"]=="enum", "h2o.H2OFrame.cut() command is not working."
    assert len(newframe.levels()) <= len(breaks), "h2o.H2OFrame.cut() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_cut())
else:
    h2o_H2OFrame_cut()
