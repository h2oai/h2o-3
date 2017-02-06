from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.backend.connection import H2OResponse

def h2oframe():
    """
    Python API test: h2o.frame(frame_id)
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    frame_summary = h2o.frame(training_data.frame_id)
    assert_is_type(frame_summary, H2OResponse)
    assert frame_summary["frames"][0]['rows']==training_data.nrow, "h2o.frame() command is not working."
    assert frame_summary["frames"][0]['column_count']==training_data.ncol, "h2o.frame() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oframe)
else:
    h2oframe()
