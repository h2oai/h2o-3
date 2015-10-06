################################################################################
##
## Verifying that Python can support user-specified strings to be treated as
## missing.
##
################################################################################
import sys, urllib
sys.path.insert(1, "../../")
import h2o, tests

def na_strings():
    path = "smalldata/jira/hexdev_29.csv"

    fhex = h2o.import_file(tests.locate(path))
    fhex.summary()
    fhex_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex._id) + "/summary")["frames"][0]["columns"]
    fhex_missing_count = sum([e["missing_count"] for e in fhex_col_summary])

    fhex_na_strings = h2o.import_file(tests.locate(path),
                           na_strings=[[],["fish", "xyz"],[]])
    fhex_na_strings.summary()
    fhex__na_strings_col_summary =  h2o.H2OConnection.get_json("Frames/" + urllib.quote(fhex_na_strings._id) + "/summary")["frames"][0]["columns"]
    fhex_na_strings_missing_count = sum([e["missing_count"] for e in fhex__na_strings_col_summary])

    assert fhex_missing_count == 0
    assert fhex_na_strings_missing_count == 2

if __name__ == "__main__":
    tests.run_test(sys.argv, na_strings)
