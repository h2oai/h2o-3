import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
################################################################################
##
## Verifying that Python can support additional parameters of destination_frame,
## column_names, and column_types and that certain characters are replaced.
##
################################################################################




def additional_parameters():

    #col_types as list
    dest_frame="dev29&hex%"
    c_names = ["a", "b", "c"]
    c_types = ["enum", "enum", "string"]

    fhex = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"),
                           destination_frame=dest_frame,
                           col_names=c_names,
                           col_types=c_types)
    fhex.describe()

    assert fhex.frame_id == dest_frame.replace("%",".").replace("&",".")
    assert fhex.col_names == c_names
    col_summary = h2o.frame(fhex.frame_id)["frames"][0]["columns"]
    for i in range(len(col_summary)):
        assert col_summary[i]["type"] == c_types[i]

    #col_types as dictionary
    dest_frame="dev29&hex%"
    c_names = ["a", "b", "c"]
    c_types = {"c":"string", "a":"string"}

    fhex = h2o.import_file(pyunit_utils.locate("smalldata/jira/hexdev_29.csv"),
                           destination_frame=dest_frame,
                           col_names=c_names,
                           col_types=c_types)
    fhex.describe()

    assert fhex.frame_id == dest_frame.replace("%",".").replace("&",".")
    assert fhex.col_names == c_names
    col_summary = h2o.frame(fhex.frame_id)["frames"][0]["columns"]
    for i in range(len(col_summary)):
      name = c_names[i]
      if name in c_types:
        assert col_summary[i]["type"] == c_types[name]



if __name__ == "__main__":
    pyunit_utils.standalone_test(additional_parameters)
else:
    additional_parameters()
