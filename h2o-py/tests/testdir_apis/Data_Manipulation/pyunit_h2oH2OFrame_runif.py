from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np


def h2o_H2OFrame_runif():
    """
    Python API test: h2o.frame.H2OFrame.runif(seed=None)
    """
    python_lists = np.random.uniform(0,1, 10000)
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    h2oRunif = h2oframe.runif(seed=None)

    # check mean and std
    assert abs(h2oframe.mean().flatten()-h2oRunif.mean()) < 1e-2, "h2o.H2OFrame.runif() command is not working."
    assert abs(h2oframe.sd()[0]-h2oRunif.sd()[0]) < 1e-2, "h2o.H2OFrame.runif() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_runif())
else:
    h2o_H2OFrame_runif()
