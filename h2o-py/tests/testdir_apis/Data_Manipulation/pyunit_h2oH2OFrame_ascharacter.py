from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_ascharacter():
    """
    Python API test: h2o.frame.H2OFrame.ascharacter()

    Copied from pyunit_ascharacter.py
    """
    h2oframe =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars.csv"))
    newFrame = h2oframe['cylinders'].ascharacter()

    assert_is_type(newFrame, H2OFrame)
    assert newFrame.isstring()[0], "h2o.H2OFrame.ascharacter() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_ascharacter())
else:
    h2o_H2OFrame_ascharacter()
