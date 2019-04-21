from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.backend.connection import H2OResponse

def h2oframes():
    """
    Python API test: h2o.frames()
    """
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))
    arrestsH2O = h2o.upload_file(pyunit_utils.locate("smalldata/pca_test/USArrests.csv"))
    prostate = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    all_frames_summary = h2o.frames()
    assert_is_type(all_frames_summary, H2OResponse)
    assert len(all_frames_summary['frames'])>=3, "h2o.frames() command is not working.  It did not fetch all 3 " \
                                                 "frame summaries."
    total_columns = training_data.ncol+arrestsH2O.ncol+prostate.ncol
    summary_total_columns = all_frames_summary['frames'][0]['columns']+all_frames_summary['frames'][1]['columns']\
                            +all_frames_summary['frames'][2]['columns']
    assert total_columns==summary_total_columns, "Wrong frame columns are returned in frame summary.  " \
                                                 "h2o.frames() command is not working."
    total_rows = training_data.nrow+arrestsH2O.nrow+prostate.nrow
    summary_total_rows = all_frames_summary['frames'][0]['rows']+all_frames_summary['frames'][1]['rows'] \
                            +all_frames_summary['frames'][2]['rows']
    assert total_rows==summary_total_rows, "Wrong frame rows are returned in frame summary.  " \
                                           "h2o.frames() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oframes)
else:
    h2oframes()
