import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np


def h2o_H2OFrame_lstrip():
    """
    Python API test: h2o.frame.H2OFrame.lstrip(set='')

    Copied from runit_lstrip.R
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris["C5"] = iris["C5"].lstrip("Iris-")
    newNames = iris["C5"].levels()[0]
    newStrip = ["etosa", "versicolor", "virginica"]

    assert newNames==newStrip, "h2o.H2OFrame.lstrip() command is not working."  # check return result


pyunit_utils.standalone_test(h2o_H2OFrame_lstrip)
