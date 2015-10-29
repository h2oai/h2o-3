import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that Python can support user-specified strings to be treated as
## missing.
##
################################################################################
import urllib


def na_strings():
    path = "smalldata/jira/hexdev_29.csv"

    fhex = h2o.import_file(pyunit_utils.locate(path))
    fhex.summary()
    fhex_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_missing_count = sum([e["missing_count"] for e in fhex_col_summary])
    assert fhex_missing_count == 0

    #na_strings as list of lists
    fhex_na_strings = h2o.import_file(pyunit_utils.locate(path),
                           na_strings=[[],["fish", "xyz"],[]])
    fhex_na_strings.summary()
    fhex__na_strings_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_na_strings.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_na_strings_missing_count = sum([e["missing_count"] for e in fhex__na_strings_col_summary])
    assert fhex_na_strings_missing_count == 2

    #na_strings as single list
    fhex_na_strings = h2o.import_file(pyunit_utils.locate(path),
                                      na_strings=["fish", "xyz"])
    fhex_na_strings.summary()
    fhex__na_strings_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_na_strings.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_na_strings_missing_count = sum([e["missing_count"] for e in fhex__na_strings_col_summary])
    assert fhex_na_strings_missing_count == 2

    #na_strings as dictionary with values as string
    fhex_na_strings = h2o.import_file(pyunit_utils.locate(path),
                                      na_strings={"h2": "fish"})
    fhex_na_strings.summary()
    fhex__na_strings_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_na_strings.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_na_strings_missing_count = sum([e["missing_count"] for e in fhex__na_strings_col_summary])
    assert fhex_na_strings_missing_count == 2

    fhex_na_strings = h2o.import_file(pyunit_utils.locate(path),
                                      na_strings={"h1": "fish"})
    fhex_na_strings.summary()
    fhex__na_strings_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_na_strings.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_na_strings_missing_count = sum([e["missing_count"] for e in fhex__na_strings_col_summary])
    assert fhex_na_strings_missing_count == 0

    #na_strings as dictionary with values as list of strings
    fhex_na_strings = h2o.import_file(pyunit_utils.locate(path),
                                      na_strings={"h2": ["fish","xyz"]})
    fhex_na_strings.summary()
    fhex__na_strings_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_na_strings.frame_id) + "/summary")["frames"][0]["columns"]
    fhex_na_strings_missing_count = sum([e["missing_count"] for e in fhex__na_strings_col_summary])
    assert fhex_na_strings_missing_count == 2



if __name__ == "__main__":
    pyunit_utils.standalone_test(na_strings)
else:
    na_strings()
