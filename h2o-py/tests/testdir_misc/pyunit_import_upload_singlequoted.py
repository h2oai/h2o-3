import sys

import h2o
from h2o.exceptions import H2OTypeError
from tests import pyunit_utils

sys.path.insert(1, "../../")


def import_upload_singlequoted():
    airlines_import = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv"),
                                      quotechar="'")
    assert airlines_import.ncols == 20
    assert airlines_import.nrows == 7

    airlines_upload = h2o.upload_file(path=pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv"),
                                      quotechar="\'")
    assert airlines_upload.ncols == 20
    assert airlines_upload.nrows == 7

    try:
        h2o.import_file(path=pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv"),
                        quotechar="f")
        assert False
    except H2OTypeError as e:
        assert e.var_name == "quotechar"

    try:
        h2o.upload_file(path=pyunit_utils.locate("smalldata/parser/single_quotes_mixed.csv"),
                        quotechar="f")
        assert False
    except H2OTypeError as e:
        assert e.var_name == "quotechar"


if __name__ == "__main__":
    pyunit_utils.standalone_test(import_upload_singlequoted)
else:
    import_upload_singlequoted()
