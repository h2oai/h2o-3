from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np

def h2o_H2OFrame_sd():
    """
    Python API test: h2o.frame.H2OFrame.sd(na_rm=False)
    """
    python_lists = np.random.uniform(1, 10, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newframe = h2oframe.scale(center=True, scale=True)
    framesd = newframe.sd()
    assert (abs(framesd[0]-1) < 1e-3) and (abs(framesd[1]-1))<1e-3, "h2o.H2OFrame.sd() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_sd())
else:
    h2o_H2OFrame_sd()
