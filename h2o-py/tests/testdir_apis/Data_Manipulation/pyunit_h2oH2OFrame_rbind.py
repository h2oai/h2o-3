from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


def h2o_H2OFrame_rbind():
    """
    Python API test: h2o.frame.H2OFrame.rbind(data)

    Copied from pyunit_cbind.py
    """
    hdf = h2o.import_file(path=pyunit_utils.locate('smalldata/jira/pub-180.csv'))
    hdfrows, hdfcols = hdf.shape

    hdf2 = hdf.rbind(hdf)
    assert_is_type(hdf2, H2OFrame)  # check new frame data type
    assert hdf2.shape==(2*hdfrows, hdfcols), "h2o.H2OFrame.rbind() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_rbind())
else:
    h2o_H2OFrame_rbind()
