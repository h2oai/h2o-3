from __future__ import print_function
from builtins import str
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from pandas import DataFrame

def h2ols():
    """
    Python API test: h2o.ls()
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    lsObject = h2o.ls()
    # check return type as DataFrame
    assert_is_type(lsObject, DataFrame)
    # check that our frame info was included in the lsObject
    assert lsObject.values[0][0] == str(iris.frame_id), \
        "Frame info iris.hex should have been found but h2o.ls() command failed."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ols)
else:
    h2ols()
