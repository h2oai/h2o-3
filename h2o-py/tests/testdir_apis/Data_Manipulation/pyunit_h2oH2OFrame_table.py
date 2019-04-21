from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_table():
    """
    Python API test: h2o.frame.H2OFrame.table(data2=None, dense=True)

    Copied from pyunit_table.py
    """
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    tableFrame = df[['DPROS','RACE']].table(data2=None, dense=True)

    assert_is_type(tableFrame, H2OFrame)
    assert tableFrame.sum(axis=0).sum(axis=1).flatten()==df.nrow, \
        "h2o.H2OFrame.table() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_table())
else:
    h2o_H2OFrame_table()
