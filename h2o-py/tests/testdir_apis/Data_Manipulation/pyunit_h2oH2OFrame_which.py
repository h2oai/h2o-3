from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
import numpy as np
from h2o.frame import H2OFrame

def h2o_H2OFrame_which():
    """
    Python API test: h2o.frame.H2OFrame.which()
    """
    python_lists = np.random.randint(1,5, (100,1))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newFrame = h2oframe.which()     # first row contains index 0.

    assert_is_type(newFrame, H2OFrame)     # check return type
    assert newFrame[1:h2oframe.nrow,0].all(), "h2o.H2OFrame.which() command is not working."  # check return result

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_which())
else:
    h2o_H2OFrame_which()
