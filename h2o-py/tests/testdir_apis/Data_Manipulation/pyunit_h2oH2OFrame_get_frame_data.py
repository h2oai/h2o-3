from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
import random

def h2o_H2OFrame_get_frame_data():
    """
    Python API test: h2o.frame.H2OFrame.get_frame_data()
    """
    h2o_iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    temp = h2o_iris.get_frame_data()
    assert_is_type(temp, str)       # check return type
    # randomly check last column and make sure they are included in the string generated from get_frame_data()
    assert h2o_iris[random.randrange(0, h2o_iris.nrow), "class"] in temp, "h2o.H2OFrame.get_frame_data()" \
                                                                          " command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_get_frame_data())
else:
    h2o_H2OFrame_get_frame_data()
