import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# test and make sure that the force_col_types works
def test_force_col_types():
    h2oOriginalTypes = {'C1': 'real', 'C2': 'int', 'C3': 'int', 'C4': 'int', 'C5': 'int', 'C6': 'string', 'C7': 'real',
                'C8': 'string', 'C9': 'real', 'C10': 'real', 'C11': 'enum', 'C12': 'int', 'C13': 'int',
                'C14': 'int', 'C15': 'int', 'C16': 'enum', 'C17': 'real', 'C18': 'real', 'C19': 'enum',
                'C20': 'enum', 'C21': 'enum', 'C22': 'real', 'C23': 'int', 'C24': 'int', 'C25': 'enum',
                'C26': 'enum', 'C27': 'string', 'C28': 'int', 'C29': 'int', 'C30': 'int', 'C31': 'int',
                'C32': 'int', 'C33': 'int', 'C34': 'int', 'C35': 'enum', 'C36': 'int', 'C37': 'string',
                'C38': 'int', 'C39': 'string', 'C40': 'int', 'C41': 'string', 'C42': 'string', 'C43': 'real',
                'C44': 'int', 'C45': 'string', 'C46': 'int', 'C47': 'real', 'C48': 'real', 'C49': 'int', 'C50': 'int'}
    # import file
    h2oData = h2o.import_file(pyunit_utils.locate("smalldata/parser/synthetic_dataset.csv")) # no col_types specification
    h2oTypes = h2oData.types
    pyunit_utils.equal_two_dicts_string(h2oOriginalTypes, h2oTypes)
    
    h2oTypes["C16"]="int"   # these will happen without force_col_type
    h2oTypes["C11"]="int"
    h2oTypes["C4"]="enum"
    h2oTypes["C21"]="int"
    h2oData2 = h2o.import_file(pyunit_utils.locate("smalldata/parser/synthetic_dataset.csv"), col_types=h2oTypes)
    h2o2Types = h2oData2.types
    pyunit_utils.equal_two_dicts_string(h2oTypes, h2o2Types)

    h2oTypes = h2oData.types # these changes needs force_col_types = True
    h2oTypes["C2"]="real"
    h2oTypes["C49"]="real"
    h2oTypes["C48"]="int"
    h2oData3 = h2o.import_file(pyunit_utils.locate("smalldata/parser/synthetic_dataset.csv"), col_types=h2oTypes)
    h2o3Types = h2oData3.types
    pyunit_utils.equal_two_dicts_string(h2oOriginalTypes, h2o3Types)

    # set force_col_types=True to Ensure changes here.
    h2oTypes["C21"]="real" # this one does not need force_col_types = True
    h2oData4 = h2o.import_file(pyunit_utils.locate("smalldata/parser/synthetic_dataset.csv"), col_types=h2oTypes, force_col_types=True)
    h2o4Types = h2oData4.types
    if h2oData4["C21"].nacnt()[0] == h2oData4.nrow: # column of NAs are treated as integer
        h2oTypes["C21"] = h2o4Types["C21"]
    pyunit_utils.equal_two_dicts_string(h2oTypes, h2o4Types)
    pyunit_utils.compare_frames_local(h2oData["C2"], h2oData4["C2"], prob=1)  # change from int column to real columns should be fine
    pyunit_utils.compare_frames_local(h2oData2["C21"], h2oData4["C21"], prob=1) # cat to int and cat to real should provide same answer
    # change from real to int will generate columns with different values due to rounding
    pyunit_utils.compare_frames_local(h2oData3["C48"], h2oData4["C48"], prob=1, tol=0.5)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_force_col_types)
else:
    test_force_col_types()
