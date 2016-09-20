from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def lists_equal(l1,l2):
    return len(l1) == len(l2) and sorted(l1) == sorted(l2)

def pyunit_filter_types():

    fr = h2o.import_file(pyunit_utils.locate("smalldata/jira/filter_type.csv"))
    fr["e"] = fr["e"].asfactor() #Not parsed as a factor

    num_type = fr.filter_type() #numeric by default
    cat_type = fr.filter_type(type="categorical")
    str_type = fr.filter_type(type="string")
    time_type = fr.filter_type(type="time")
    uuid_type = fr.filter_type(type="uuid")
    bad_type = fr.filter_type(type="bad")

    assert lists_equal(num_type, [0,2]),"Expected numeric type in column indexes 0,2"
    assert lists_equal(cat_type, [4]),"Expected categorical type in column indexes 4"
    assert lists_equal(str_type, [1]),"Expected string type in column indexes 1"
    assert lists_equal(time_type, [3]),"Expected time type in column indexes 3"
    assert lists_equal(uuid_type, [5]),"Expected uuid type in column indexes 5"
    assert lists_equal(bad_type, [2]),"Expected bad type in column indexes 2"

if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_filter_types)
else:
    pyunit_filter_types()