from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_strsplit():
    """
    Python API test: h2o.frame.H2OFrame.strsplit(pattern)

    Copied from pyunit_strsplit.py
    """
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    result = frame["C5"].strsplit("-")
    assert_is_type(result, H2OFrame)
    assert result.nrow == 150 and result.ncol == 2
    assert result[0,0] == "Iris" and result[0,1] == "setosa", "Expected 'Iris' and 'setosa', but got {0} and " \
                                                              "{1}".format(result[0,0], result[0,1])

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_strsplit())
else:
    h2o_H2OFrame_strsplit()
