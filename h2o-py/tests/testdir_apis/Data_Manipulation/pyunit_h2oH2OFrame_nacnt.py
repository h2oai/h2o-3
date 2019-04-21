from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_na_omit():
    """
    Python API test: h2o.frame.H2OFrame.na_omit()

    Copied from runit_lstrip.R
    """
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader_NA_2.csv"))
    newframe=iris.nacnt()
    assert_is_type(newframe, list)
    assert sum(newframe)==17,  "h2o.H2OFrame.nacnt() command is not working."  # check return result

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_na_omit())
else:
    h2o_H2OFrame_na_omit()
