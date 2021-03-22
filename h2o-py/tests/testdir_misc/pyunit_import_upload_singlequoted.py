import sys

import pandas as pd

import h2o
from h2o.exceptions import H2OTypeError
from tests import pyunit_utils

sys.path.insert(1, "../../")


def test_import_single_quoted():
    path = pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv")
    hdf = h2o.import_file(path=path, quotechar="'")
    assert hdf.ncols == 20
    assert hdf.nrows == 7

    # comparison with pandas fails for this dataset if the quotechar is not forwarded correctly 
    # from the parse setup result to the full parser.
    pdf = pd.read_csv(path, quotechar="'")
    pd.testing.assert_frame_equal(pdf, hdf.as_data_frame(), check_dtype=False)


def test_upload_single_quoted():
    path = pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv")
    hdf = h2o.upload_file(path=path, quotechar="'")
    assert hdf.ncols == 20
    assert hdf.nrows == 7
    pdf = pd.read_csv(path, quotechar="'")
    pd.testing.assert_frame_equal(pdf, hdf.as_data_frame(), check_dtype=False)


def test_import_fails_on_unsupported_quotechar():
    try:
        h2o.import_file(path=pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv"),
                        quotechar="f")
        assert False
    except H2OTypeError as e:
        assert e.var_name == "quotechar"


def test_upload_fails_on_unsupported_quotechar():
    try:
        h2o.upload_file(path=pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv"),
                        quotechar="f")
        assert False
    except H2OTypeError as e:
        assert e.var_name == "quotechar"


pyunit_utils.run_tests([
    test_import_single_quoted,
    test_upload_single_quoted,
    test_import_fails_on_unsupported_quotechar,
    test_upload_fails_on_unsupported_quotechar,
])
