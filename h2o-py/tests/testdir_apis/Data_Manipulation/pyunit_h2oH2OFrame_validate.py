from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame():
    """
    Python API test: h2o.frame.H2OFrame(python_obj=None, destination_frame=None, header=0, separator=', ',
    column_names=None, column_types=None, na_strings=None)
    """
    python_lists = [[1,4,"a",1], [2,5,"b",0], [3,6,"c",1]]
    col_names=["num1","num2","str1","enum1"]
    dest_frame="newFrame"
    heads=-1
    sep=','
    col_types=['numeric', 'numeric', 'string', 'enum']
    na_str=['NA']
    h2oframe = h2o.H2OFrame(python_obj=python_lists, destination_frame=dest_frame, header=heads, separator=sep,
                            column_names=col_names, column_types=col_types, na_strings=na_str)
    assert_is_type(h2oframe, H2OFrame)
    assert h2oframe.nrows==len(python_lists) and h2oframe.ncols==len(python_lists[0]), "h2o.H2OFrame() command is " \
                                                                                 "not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame())
else:
    h2o_H2OFrame()
