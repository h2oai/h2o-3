################################################################################
##
## Verifying that Python can support user-specified separator characters.
##
################################################################################
import sys, os, urllib
sys.path.insert(1, "../../")
import h2o


def separator(ip, port):

    path = "smalldata/jira/hexdev_29.csv"

    fhex = h2o.import_file(h2o.locate(path), sep=",")
    fhex.summary()
    fhex_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex._id) + "/summary")["frames"][0]["columns"]
    fhex_missing_count = sum([e["missing_count"] for e in fhex_col_summary])
    assert fhex_missing_count == 0

    fhex_wrong_separator = h2o.import_file(h2o.locate(path), sep=";")
    fhex_wrong_separator.summary()
    fhex_wrong_separator_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_wrong_separator._id) + "/summary")["frames"][0]["columns"]
    fhex_wrong_separator_missing_count = sum([e["missing_count"] for e in fhex_wrong_separator_col_summary])
    assert fhex_wrong_separator_missing_count == fhex_wrong_separator._nrows*fhex_wrong_separator._ncols

    try:
        h2o.import_file(h2o.locate(path), sep="--")
    except ValueError:
        pass
    else:
        assert False

if __name__ == "__main__":
    h2o.run_test(sys.argv, separator)
