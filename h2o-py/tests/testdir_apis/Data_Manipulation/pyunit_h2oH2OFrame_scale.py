from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import numpy as np


def h2o_H2OFrame_scale():
    """
    Python API test: h2o.frame.H2OFrame.scale(center=True, scale=True)
    """
    python_lists = np.random.uniform(1, 10, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newframe = h2oframe.scale(center=True, scale=True)
    frameMean = newframe.mean()
    framesd = newframe.sd()

    assert_is_type(newframe, H2OFrame)
    assert (abs(frameMean[0,0]) < 1e-3) and (abs(frameMean[0,1]) < 1e-3), \
        "h2o.H2OFrame.scale() command is not working."
    assert (abs(framesd[0]-1) < 1e-3) and (abs(framesd[1]-1))<1e-3, "h2o.H2OFrame.scale() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_scale())
else:
    h2o_H2OFrame_scale()
