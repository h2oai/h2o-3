import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_get_frame():
    """
    Python API test: h2o.frame.H2OFrame.get_frame()
    """
    frame1 = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"))
    frame2 = h2o.get_frame(frame1.frame_id)
    assert_is_type(frame2, H2OFrame)


pyunit_utils.standalone_test(h2o_H2OFrame_get_frame)
