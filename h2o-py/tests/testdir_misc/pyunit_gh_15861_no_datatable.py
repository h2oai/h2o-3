import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_use_datatable)
    
def test_datatable_without_datatable():
    delTable = False
    if can_use_datatable():
        delTable = True
        pyunit_utils.uninstall("datatable")
        
    try:   
        # should run to completion
        with pyunit_utils.catch_warnings() as ws:
            h2oFrame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"))
            new_frame = h2oFrame.as_data_frame(multi_thread=True) 
            assert "multi_thread mode can only be used when you have datatable installed.  Defaults to single-thread " \
                "operation." in str(ws[0].message)
    finally:
        # re-install datatable before quitting.     
        if delTable:
            pyunit_utils.install("datatable")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_datatable_without_datatable)
else:
    test_datatable_without_datatable()
