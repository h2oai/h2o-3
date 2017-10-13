from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

def h2o_H2OFrame_describe():
    """
    Python API test: h2o.frame.H2OFrame.describe(chunk_summary=False))
    """
    python_lists = [[1,2,3],[4,5,6],["a","b","c"], [1,0,1]]
    col_names=["num1","num2","str1","enum1"]
    dest_frame="newFrame"
    heads=-1
    sep=','
    col_types=['numeric', 'numeric', 'string', 'enum']
    na_str=['NA']
    h2oframe = h2o.H2OFrame(python_obj=python_lists, destination_frame=dest_frame, header=heads, separator=sep,
                            column_names=col_names, column_types=col_types, na_strings=na_str)
    h2oframe.describe(chunk_summary=True)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_describe())
else:
    h2o_H2OFrame_describe()
