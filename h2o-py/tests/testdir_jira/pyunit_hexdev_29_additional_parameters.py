################################################################################
##
## Verifying that Python can support additional parameters of destination_frame,
## column_names, and column_types and that certain characters are replaced.
##
################################################################################
import sys, os
sys.path.insert(1, "../../")
import h2o, tests

def additional_parameters():
    dest_frame="dev29&hex%"
    c_names = ["a", "b", "c"]
    c_types = ["enum", "enum", "enum"]

    fhex = h2o.import_file(tests.locate("smalldata/jira/hexdev_29.csv"),
                           destination_frame=dest_frame,
                           col_names=c_names,
                           col_types=c_types)
    fhex.describe()

    assert fhex._id == dest_frame.replace("%",".").replace("&",".")
    assert fhex._col_names == c_names
    col_summary = h2o.frame(fhex._id)["frames"][0]["columns"]
    for i in range(len(col_summary)):
        assert col_summary[i]["type"] == c_types[i]

if __name__ == "__main__":
    tests.run_test(sys.argv, additional_parameters)
