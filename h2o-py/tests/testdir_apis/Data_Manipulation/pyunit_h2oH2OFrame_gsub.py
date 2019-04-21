from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def h2o_H2OFrame_gsub():
    """
    Python API test: h2o.frame.H2OFrame.gsub(pattern, replacement, ignore_case=False)

    Copied from pyunit_sub_gsub.py
    """
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"),
                            col_types=["numeric","numeric","numeric","numeric","string"])

    frame["C5"] = frame["C5"].gsub("s", "z", ignore_case=False)
    assert frame[0,4] == "Iriz-zetoza", "Expected 'Iriz-zetoza', but got {0}".format(frame[0,4])


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_gsub())
else:
    h2o_H2OFrame_gsub()
