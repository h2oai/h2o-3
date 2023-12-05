import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.threading import local_context
    
def test_datatable_without_datatable():  
    # should run to completion
    with local_context(datatable_disabled=True, polars_disabled=True):
        with pyunit_utils.catch_warnings() as ws:
            h2oFrame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"))
            new_frame = h2oFrame.as_data_frame() 
            assert "converting H2O frame to pandas dataframe using single-thread.  For faster conversion using"
            " multi-thread, install datatable (for Python 3.9 or lower), or polars and pyarrow "
            "(for Python 3.10 or above)" in str(ws[0].message)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_datatable_without_datatable)
else:
    test_datatable_without_datatable()
