from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_nrow():
    """
    Python API test: h2o.frame.H2OFrame.nrow, h2o.frame.H2OFrame.nrows, h2o.frame.H2OFrame.ncol,
    h2o.frame.H2OFrame.ncols, h2o.frame.H2OFrame.dim
    """
    ncol=5
    nrow=150
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader_NA_2.csv"))
    assert iris.nrow==nrow, "h2o.H2OFrame.nrow command is not working."
    assert iris.nrows==nrow, "h2o.H2OFrame.nrows command is not working."
    assert iris.ncol==ncol, "h2o.H2OFrame.ncol command is not working."
    assert iris.ncols==ncol, "h2o.H2OFrame.ncols command is not working."
    assert iris.dim==[nrow,ncol], "h2o.H2OFrame.dim command is not working."
    assert iris.shape==(nrow, ncol), "h2o.H2OFrame.shape command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_nrow())
else:
    h2o_H2OFrame_nrow()
