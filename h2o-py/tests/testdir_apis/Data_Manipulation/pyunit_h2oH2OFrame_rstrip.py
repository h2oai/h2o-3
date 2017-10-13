from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_rstrip():
    """
    Python API test: h2o.frame.H2OFrame.rstrip(set='')

    Copied from runit_lstrip.R
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris["C5"] = iris["C5"].rstrip("color")
    newNames = iris["C5"].levels()[0]
    newStrip = ['Iris-setosa', 'Iris-versi', 'Iris-virginica']

    assert newNames==newStrip, "h2o.H2OFrame.rstrip() command is not working."  # check return result


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_rstrip())
else:
    h2o_H2OFrame_rstrip()
