from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2oimport_file():
    """
    Python API test: h2o.import_file(path=None, destination_frame=None, parse=True, header=0, sep=None,
    col_names=None, col_types=None, na_strings=None)
    """
    col_types=['enum','numeric','enum','enum','enum','numeric','numeric','numeric']
    col_headers = ["CAPSULE","AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"]
    hex_key = "training_data.hex"
    training_data = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"),
                                    destination_frame=hex_key, header=1, sep = ',',
                                    col_names=col_headers, col_types=col_types, na_strings=["NA"])
    assert_is_type(training_data, H2OFrame)
    assert training_data.frame_id == hex_key, "frame_id was not assigned correctly.  h2o.import_file() is not" \
                                              " working."
    assert len(set(training_data.col_names) & set(col_headers))==len(col_headers), "column names are incorrect.  " \
                                                                                   "h2o.import_file() not working."
    assert training_data.nrow==380, "number of rows is incorrect.  h2o.import_file() is not working."
    assert training_data.ncol==8, "number of columns is incorrect.  h2o.import_file() is not working."
    assert sum(training_data.nacnt())==3, "NA count is incorrect.  h2o.import_file() is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2oimport_file)
else:
    h2oimport_file()
