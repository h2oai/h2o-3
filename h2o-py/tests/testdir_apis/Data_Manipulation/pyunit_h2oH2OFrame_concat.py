from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_concat():
    """
    Python API test: h2o.frame.H2OFrame.concat(frames, axis=1)

    Copied from pyunit_concat.py
    """
    df1 = h2o.create_frame(integer_fraction=1,binary_fraction=0,categorical_fraction=0,seed=1)
    df2 = h2o.create_frame(integer_fraction=1,binary_fraction=0,categorical_fraction=0,seed=2)
    df3 = h2o.create_frame(integer_fraction=1,binary_fraction=0,categorical_fraction=0,seed=3)

    # frame to frame concat (column-wise)
    df123 = df1.concat([df2,df3])
    assert_is_type(df123, H2OFrame)     # check return type
    assert df123.shape==(df1.nrows, df1.ncols+df2.ncols+df3.ncols), "h2o.H2OFrame.concat command is not working."#

    #Frame to Frame concat (row wise)
    df123_row = df1.concat([df2,df3], axis = 0)
    assert_is_type(df123_row, H2OFrame)     # check return type
    assert df123_row.shape==(df1.nrows+df2.nrows+df3.nrows, df1.ncols), \
        "h2o.H2OFrame.concat command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_concat())
else:
    h2o_H2OFrame_concat()
