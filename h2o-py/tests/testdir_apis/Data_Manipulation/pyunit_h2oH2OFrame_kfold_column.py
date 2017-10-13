from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np
from tests import pyunit_utils
from h2o.frame import H2OFrame


def h2o_H2OFrame_kfold_column():
    """
    Python API test: h2o.frame.H2OFrame.kfold_column(n_folds=3, seed=-1)
    """
    python_lists = np.random.randint(-5,5, (1000, 2))
    k = randrange(2,10)
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    clist = h2oframe.kfold_column(n_folds=k, seed=12345)

    assert_is_type(clist, H2OFrame)     # check return type
    assert clist.asfactor().nlevels()[0]==k, "h2o.H2OFrame.kfold_column() command is not working."  # check return result


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_kfold_column())
else:
    h2o_H2OFrame_kfold_column()
