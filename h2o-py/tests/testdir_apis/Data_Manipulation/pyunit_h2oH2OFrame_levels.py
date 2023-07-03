import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np


def h2o_H2OFrame_levels():
    """
    Python API test: h2o.frame.H2OFrame.levels()
    """
    python_lists = np.random.randint(-2,2, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists, column_types=['enum', 'enum'])
    clist = h2oframe.levels()

    assert_is_type(clist, list)     # check return type
    assert len(clist)==2, "h2o.H2OFrame.levels() command is not working."  # check list length

pyunit_utils.standalone_test(h2o_H2OFrame_levels)
