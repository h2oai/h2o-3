from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_nchar():
    """
    Python API test: h2o.frame.H2OFrame.nchar()
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader_NA_2.csv"))
    newframe=iris[4].nchar()
    assert_is_type(newframe, H2OFrame)
    assert abs(newframe.sum().flatten()-(11+14+15)*50)< 1e-6, "h2o.H2OFrame.nchar() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_nchar())
else:
    h2o_H2OFrame_nchar()
