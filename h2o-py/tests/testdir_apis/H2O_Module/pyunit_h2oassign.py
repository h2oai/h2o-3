from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2oassign():
    """
    Python API test: h2o.assign(data, xid)
    """
    old_name = "benign.csv"
    new_name = "newBenign.csv"
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"), destination_frame=old_name)
    assert training_data.frame_id==old_name, "h2o.import_file() is not working.  Wrong frame_id is assigned."
    temp=h2o.assign(training_data, new_name)
    assert_is_type(temp, H2OFrame)
    assert training_data.frame_id==new_name, "h2o.assign() is not working.  New frame_id is not assigned."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oassign)
else:
    h2oassign()
