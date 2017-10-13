from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type

def h2o_H2OFrame_columns_by_type():
    """
    Python API test: h2o.frame.H2OFrame.columns_by_type(coltype='numeric')

    Copied from pyunit_colnames_by_type.py
    """
    fr = h2o.import_file(pyunit_utils.locate("smalldata/jira/filter_type.csv"))

    #Positive case. Look for all coltypes
    num_type = fr.columns_by_type() #numeric by default
    cat_type = fr.columns_by_type(coltype="categorical")
    str_type = fr.columns_by_type(coltype="string")
    time_type = fr.columns_by_type(coltype="time")
    uuid_type = fr.columns_by_type(coltype="uuid")
    bad_type = fr.columns_by_type(coltype="bad")

    assert_is_type(num_type, list)  # check for correct return type
    assert_is_type(cat_type, list)
    assert_is_type(str_type, list)
    assert_is_type(time_type, list)
    assert_is_type(uuid_type, list)
    assert_is_type(bad_type, list)

    # check for correct grouping
    assert 2.0 in bad_type, "h2o.H2OFrame.columns_by_type command is not working."
    assert (0.0 in num_type) and (2.0 in num_type), "h2o.H2OFrame.columns_by_type command is not working."
    assert (1.0 in str_type) and (4.0 in str_type), "h2o.H2OFrame.columns_by_type command is not working."
    assert 3.0 in time_type, "h2o.H2OFrame.columns_by_type command is not working."
    assert 5.0 in uuid_type, "h2o.H2OFrame.columns_by_type command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_columns_by_type())
else:
    h2o_H2OFrame_columns_by_type()
