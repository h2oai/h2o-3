from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def pyunit_columns_by_types():

    #Positive case. All coltypes available
    fr = h2o.import_file(pyunit_utils.locate("smalldata/jira/filter_type.csv"))
    fr["e"] = fr["e"].asfactor() #Not parsed as a factor

    #Negative case frame. All coltypes are ints
    frame = h2o.create_frame(rows=10,integer_fraction=1,binary_ones_fraction=0, missing_fraction=0)

    #Positive case. Look for all coltypes
    num_type = fr.columns_by_type() #numeric by default
    cat_type = fr.columns_by_type(coltype="categorical")
    str_type = fr.columns_by_type(coltype="string")
    time_type = fr.columns_by_type(coltype="time")
    uuid_type = fr.columns_by_type(coltype="uuid")
    bad_type = fr.columns_by_type(coltype="bad")

    #Negative case. Look for categoricals,strings,times,uuid, and bad when there are none. Should return an empty list.
    neg_cat = frame.columns_by_type(coltype="categorical")
    neg_string = frame.columns_by_type(coltype="string")
    neg_time = frame.columns_by_type(coltype="time")
    neg_uuid = frame.columns_by_type(coltype="uuid")
    neg_bad = frame.columns_by_type(coltype="bad")

    #Positive Test
    assert num_type == [0,2],"Expected numeric type in column indexes 0,2"
    assert cat_type == [4],"Expected categorical type in column indexes 4"
    assert str_type == [1],"Expected string type in column indexes 1"
    assert time_type == [3],"Expected time type in column indexes 3"
    assert uuid_type == [5],"Expected uuid type in column indexes 5"
    assert bad_type == [2],"Expected bad type in column indexes 2"

    #Negative test
    assert neg_cat == [],"Expect an empty list since there are no categoricals"
    assert neg_string == [],"Expect an empty list since there are no string"
    assert neg_time == [],"Expect an empty list since there are no time variables"
    assert neg_uuid == [],"Expect an empty list since there are no uuids"
    assert neg_bad == [],"Expect an empty list since there are no bad variable types"
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_columns_by_types)
else:
    pyunit_columns_by_types()