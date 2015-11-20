import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that Python can support user-specified separator characters.
##
################################################################################
import urllib



def separator():

    path = "smalldata/jira/hexdev_29.csv"

    fhex = h2o.import_file(pyunit_utils.locate(path), sep=",")
    fhex.summary()
    fhex_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_missing_count = sum([e["missing_count"] for e in fhex_col_summary])
    assert fhex_missing_count == 0

    # since the wrong separator was passed, we will parse the data as one big column.
    # Test proposed by Eric.
    fhex_wrong_separator = h2o.import_file(pyunit_utils.locate(path), sep=";")
    fhex_wrong_separator.summary()
    assert fhex_wrong_separator.ncol == 1
    assert fhex_wrong_separator.nrow == 6

    try:
        h2o.import_file(pyunit_utils.locate(path), sep="--")
    except ValueError:
        pass
    else:
        assert False



if __name__ == "__main__":
    pyunit_utils.standalone_test(separator)
else:
    separator()
