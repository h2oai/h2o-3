import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type

def h2o_H2OFrame_names():
    """
    Python API test: h2o.frame.H2OFrame.names

    Copied from runit_lstrip.R
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader_NA_2.csv"))
    newframe=iris.names
    assert_is_type(newframe, list)
    assert len(newframe)==iris.ncol,  "h2o.H2OFrame.names command is not working."  # check return result


pyunit_utils.standalone_test(h2o_H2OFrame_names)
