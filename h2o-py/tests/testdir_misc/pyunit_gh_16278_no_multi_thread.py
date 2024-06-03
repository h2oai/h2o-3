import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.threading import local_context
from h2o.utils.shared_utils import (can_use_polars)
    
def test_datatable_without_multi_thread():  
    # should run to completion
    with local_context(polars_disabled=True):
        with pyunit_utils.catch_warnings() as ws:
            h2o_frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"))
            h2o_frame.as_data_frame() 
            assert not(can_use_polars()) or "Converting H2O frame to pandas dataframe using single-thread.  For faster " \
                                            "conversion using multi-thread, install polars and pyarrow and use it as " \
                                            "" in str(ws[0].message)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_datatable_without_multi_thread)
else:
    test_datatable_without_multi_thread()
