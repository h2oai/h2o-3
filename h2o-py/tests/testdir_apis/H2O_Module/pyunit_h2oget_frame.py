from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2oget_frame():
    """
    Python API test: h2o.get_frame(frame_id)
    """
    frame1 = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"))
    frame2 = h2o.get_frame(frame1.frame_id)
    assert_is_type(frame2, H2OFrame)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oget_frame)
else:
    h2oget_frame()
