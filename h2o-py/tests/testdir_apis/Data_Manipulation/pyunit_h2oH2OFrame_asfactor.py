import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_asfactor():
    """
    Python API test: h2o.frame.H2OFrame.asfactor()

    Copied from pyunit_ascharacter.py
    """
    h2oframe =  h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars.csv"))
    newFrame = h2oframe['cylinders'].asfactor()

    assert_is_type(newFrame, H2OFrame)
    assert newFrame.isfactor()[0], "h2o.H2OFrame.asfactor() command is not working."


pyunit_utils.standalone_test(h2o_H2OFrame_asfactor)
