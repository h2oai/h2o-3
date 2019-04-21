from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_split_frame():
    """
    Python API test: h2o.frame.H2OFrame.split_frame(ratios=None, destination_frames=None, seed=None)
    """
    python_lists = np.random.uniform(-1,1, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newframe = h2oframe.split_frame(ratios=[0.5, 0.25], destination_frames=["f1", "f2", "f3"], seed=None)
    assert_is_type(newframe, list)
    assert_is_type(newframe[0], H2OFrame)
    assert len(newframe)==3, "h2o.H2OFrame.split_frame() command is not working."
    assert h2oframe.nrow==(newframe[0].nrow+newframe[1].nrow+newframe[2].nrow), "h2o.H2OFrame.split_frame() command " \
                                                                                "is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_split_frame())
else:
    h2o_H2OFrame_split_frame()
