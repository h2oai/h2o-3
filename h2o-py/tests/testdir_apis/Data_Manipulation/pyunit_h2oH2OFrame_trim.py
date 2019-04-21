from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2o_H2OFrame_trim():
    """
    Python API test: h2o.frame.H2OFrame.trim()

    Copied frm pyunit_trim.py
    """
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_trim.csv"),
                            col_types=["string","numeric","numeric","numeric","numeric","numeric","numeric","numeric"])

    # single column (frame)
    trimmed_frame = frame["name"].trim()
    assert trimmed_frame[0,0] == "AMC Ambassador Brougham", "Expected 'AMC Ambassador Brougham', " \
                                                            "but got {}".format(trimmed_frame[0,0])
    assert trimmed_frame[1,0] == "AMC Ambassador DPL", "Expected 'AMC Ambassador DPL', " \
                                                       "but got {}".format(trimmed_frame[1,0])
    assert trimmed_frame[2,0] == "AMC Ambassador SST", "Expected 'AMC Ambassador SST', " \
                                                       "but got {}".format(trimmed_frame[2,0])


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_trim())
else:
    h2o_H2OFrame_trim()
