from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2o_H2OFrame_types():
    """
    Python API test: h2o.frame.H2OFrame.types

    Copied frm pyunit_trim.py
    """
    column_types = ["string","numeric","numeric","numeric","numeric","numeric","numeric","numeric"]
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_trim.csv"), col_types=column_types)
    frameTypes = frame.types

    index = 0
    for cnames in frame.names:
        assert frameTypes[cnames]==frame.type(cnames), "h2o.H2OFrame.types command is not working."
        assert frame.type(index)==column_types[index] or frame.type(index)=="int" or frame.type(index)=="real", \
            "h2o.H2OFrame.type() command is not working."
        index+=1

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_types())
else:
    h2o_H2OFrame_types()
