from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_flatten():
    """
    Python API test: h2o.frame.H2OFrame.flatten()

    copied from pyunit_entropy.py
    """
    frame = h2o.H2OFrame.from_python(["redrum"])
    g = frame.flatten()
    assert g=="redrum", "h2o.H2OFrame.flatten() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_flatten())
else:
    h2o_H2OFrame_flatten()
