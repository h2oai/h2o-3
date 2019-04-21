from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type

def h2o_H2OFrame_skewness():
    """
    Python API test: h2o.frame.H2OFrame.skewness(na_rm=False)
    """
    python_lists = np.random.uniform(-1,1, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newframe = h2oframe.skewness()
    assert_is_type(newframe, list)
    assert len(newframe)==2, "h2o.H2OFrame.skewness() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_skewness())
else:
    h2o_H2OFrame_skewness()
