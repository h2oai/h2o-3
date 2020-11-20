from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import os


def test_parser_svmlight_column_skip():
    # generate a frame
    nrow = 10000
    ncol = 10
    seed = 12345
    original_frame = h2o.create_frame(rows=nrow, cols=ncol, real_fraction=0.5, integer_fraction=0.5, missing_fraction=0,
                                      has_response=True, seed=seed)

    results_path = pyunit_utils.locate("results")
    svmfile = os.path.join(results_path, 'out.svm')

    # write h2o frame to svm format
    pyunit_utils.write_H2OFrame_2_SVMLight(svmfile, original_frame)

    # check if frame uploaded/imported from svm file is equal to original frame
    svm_frame_uploaded = h2o.upload_file(svmfile)
    assert pyunit_utils.compare_frames_local_svm(original_frame, svm_frame_uploaded, prob=1, returnResult=True),\
        "Frame uploaded from svm file is not the same as original"

    svm_frame_imported = h2o.import_file(svmfile)
    assert pyunit_utils.compare_frames_local_svm(original_frame, svm_frame_imported, prob=1, returnResult=True), \
        "Frame imported from svm file is not the same as original"

    # test with null skipped_column list
    svm_frame_uploaded_skipped_nothing = h2o.upload_file(svmfile, skipped_columns=[])
    assert pyunit_utils.compare_frames_local_svm(original_frame, svm_frame_uploaded_skipped_nothing, prob=1,
                                                 returnResult=True),\
        "Frame uploaded from svm file with empty skipped_columns parameter is not the same as original"

    svm_frame_imported_skipped_nothing = h2o.import_file(svmfile, skipped_columns=[])
    assert pyunit_utils.compare_frames_local_svm(original_frame, svm_frame_imported_skipped_nothing, prob=1,
                                                 returnResult=True),\
        "Frame imported from svm file with empty skipped_columns parameter is not the same as original"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_parser_svmlight_column_skip)
else:
    test_parser_svmlight_column_skip()
