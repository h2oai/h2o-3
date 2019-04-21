from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2o_H2OFrame_substring():
    """
    Python API test: h2o.frame.H2OFrame.substring(start_index, end_index=None)

    Copied from pyunit_sub_gsub.py
    """
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    frame["C5"]= frame["C5"].substring(0,5)

    assert (frame["C5"]=='Iris-').sum() == frame.nrow, \
        "h2o.H2OFrame.substring() command is not working."



if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_substring())
else:
    h2o_H2OFrame_substring()
