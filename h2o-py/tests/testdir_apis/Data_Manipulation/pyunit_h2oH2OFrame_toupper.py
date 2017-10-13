from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np


def h2o_H2OFrame_toupper():
    """
    Python API test: h2o.frame.H2OFrame.toupper()
    """
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    frame["C5"]= frame["C5"].toupper()

    assert (frame["C5"]=='IRIS-SETOSA').sum() == 50, \
        "h2o.H2OFrame.toupper() command is not working."


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_toupper())
else:
    h2o_H2OFrame_toupper()
