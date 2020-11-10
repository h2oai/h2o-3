from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import os
from h2o.exceptions import H2OResponseError


def test_parser_svmlight_column_skip_not_supported():
    print("Test that functions calling fail if skipped_columns is passed with svm file.")
    # generate a big frame with all datatypes and save it to svmlight
    nrow = 10
    ncol = 10
    seed = 12345

    f1 = h2o.create_frame(rows=nrow, cols=ncol, real_fraction=0.5, integer_fraction=0.5, missing_fraction=0.2,
                          has_response=False, seed=seed)

    results_path = pyunit_utils.locate("results")

    savefilenamewithpath = os.path.join(results_path, 'out.svm')
    pyunit_utils.write_H2OFrame_2_SVMLight(savefilenamewithpath, f1)  # write h2o frame to svm format

    try:
        print("Test upload SVM file. "
              "Expected result is Java exception error: skipped_columns not supported for AVRO and SVMlight")
        h2o.upload_file(savefilenamewithpath, skipped_columns=[5])
        assert False, "Test should have thrown an exception due skipped_columns parameter is present"  # should have failed here
    except H2OResponseError as e:
        assert "skipped_columns are not supported" in str(e.args[0].exception_msg), "Exception message is different"
        print("Test OK, finished with H2OResponseError")

    try:
        print("Test import SVM file. "
              "Expected result is Java exception error: skipped_columns not supported for AVRO and SVMlight")
        h2o.import_file(savefilenamewithpath, skipped_columns=[5])
        assert False, "Test should have thrown an exception due skipped_columns parameter is present"  # should have failed here
    except H2OResponseError as e:
        assert "skipped_columns are not supported" in e.args[0].exception_msg, "Exception message is different"
        print("Test OK, finished with H2OResponseError")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_parser_svmlight_column_skip_not_supported)
else:
    test_parser_svmlight_column_skip_not_supported()
