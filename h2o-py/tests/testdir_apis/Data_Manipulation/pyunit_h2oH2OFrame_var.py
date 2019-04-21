from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
import numpy as np
from h2o.frame import H2OFrame


def h2o_H2OFrame_var():
    """
    Python API test: h2o.frame.H2OFrame.var(y=None, na_rm=False, use=None)

    Copied from pyunit_var.py
    """
    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    iris_np = np.genfromtxt(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"),
                            delimiter=',',
                            skip_header=1,
                            usecols=(0, 1, 2, 3))

    var_np = np.var(iris_np, axis=0, ddof=1)
    for i in range(4):
        var_h2o = iris_h2o.var(y=iris_h2o[i], na_rm=True, use=None)
        assert_is_type(var_h2o, H2OFrame)
        assert abs(var_np[i] - var_h2o[i,0]) < 1e-10, "h2o.H2OFrame.var() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_var())
else:
    h2o_H2OFrame_var()
