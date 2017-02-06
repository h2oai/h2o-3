from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_dim():
    """
    Python API test: h2o.frame.H2OFrame.drop(index, axis=1)

    copied from pyunit_drop.py
    """
    pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    nc = pros.ncol
    nr = pros.nrow

    h2oframe1 = pros.drop([pros.names[0]])
    assert h2oframe1.dim==[nr, nc-1], "h2o.H2OFrame.drop() command is not working."
    h2oframe2=h2oframe1.drop([0], axis=0)
    assert h2oframe2.dim==[nr-1, nc-1], "h2o.H2OFrame.drop() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_dim())
else:
    h2o_H2OFrame_dim()
