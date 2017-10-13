from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_asnumeric():
    """
    Python API test: h2o.frame.H2OFrame.asnumeric()

    Copied from pyunit_glrm_PUBDEV_3728_binary_numeric.py
    """
    prostateF = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    newFrame = prostateF[[0,4]].asfactor()

    assert_is_type(newFrame, H2OFrame)
    assert newFrame.isfactor()[0] and newFrame.isfactor()[newFrame.ncols-1], \
        "h2o.H2OFrame.asfactor() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_asnumeric())
else:
    h2o_H2OFrame_asnumeric()
