import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that Python can support user-specified handling of the first line
## of the file as either headers (1), to-be-guessed (0), or data (-1).
##
################################################################################


def header():

    path = "smalldata/jira/hexdev_29.csv"

    fhex_header_true = h2o.import_file(pyunit_utils.locate(path), header=1)

    fhex_header_unknown = h2o.import_file(pyunit_utils.locate(path), header=0)

    fhex_header_false = h2o.import_file(pyunit_utils.locate(path), header=-1)

    fhex_header_unspecified = h2o.import_file(pyunit_utils.locate(path))

    try:
        h2o.import_file(pyunit_utils.locate(path), header=2)
        assert False
    except ValueError:
        pass

    assert fhex_header_true.nrow == fhex_header_false.nrow - 1
    assert fhex_header_unknown.nrow == fhex_header_false.nrow or fhex_header_unknown.nrow == fhex_header_true.nrow
    assert fhex_header_unspecified.nrow == fhex_header_false.nrow or fhex_header_unspecified.nrow == fhex_header_true.nrow



if __name__ == "__main__":
    pyunit_utils.standalone_test(header)
else:
    header()
