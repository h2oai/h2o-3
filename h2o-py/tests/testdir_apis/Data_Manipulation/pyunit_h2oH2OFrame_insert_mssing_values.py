from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from random import randrange
from h2o.frame import H2OFrame
from h2o.utils.typechecks import assert_is_type
import numpy as np
import random


def h2o_H2OFrame_insert_missing_values():
    """
    Python API test: h2o.frame.H2OFrame.insert_missing_values(fraction=0.1, seed=None)
    """
    python_lists = np.random.uniform(-1,1, (10000, 10))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)

    fraction = random.uniform(0,1)
    h2oframe.insert_missing_values(fraction=fraction, seed=None)
    fract_NAs = sum(h2oframe.nacnt())/(h2oframe.ncol*h2oframe.nrow)
    assert abs(fraction-fract_NAs) < 1e-2, "h2o.H2OFrame.insert_missing_values() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_insert_missing_values())
else:
    h2o_H2OFrame_insert_missing_values()
