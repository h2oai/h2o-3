import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def concat():
    df1 = h2o.create_frame(integer_fraction=1,binary_fraction=0,categorical_fraction=0,seed=1)
    df2 = h2o.create_frame(integer_fraction=1,binary_fraction=0,categorical_fraction=0,seed=2)
    df3 = h2o.create_frame(integer_fraction=1,binary_fraction=0,categorical_fraction=0,seed=3)

    print(df1)
    print(df2)
    print(df3)

    #Frame to Frame concat (column-wise)
    df123 = df1.concat([df2,df3])
    rows, cols = df123.dim
    print(rows,cols)
    print(df123)
    assert rows == 10000 and cols == 30, "unexpected dimensions in column concatenation for a Frame"

    #Frame to Frame concat (row wise)
    df123_row = df1.concat([df2,df3], axis = 0)
    rows2, cols2 = df123_row.dim
    print(rows2,cols2)
    print(df123_row)
    assert rows2 == 30000 and cols2 == 10, "unexpected dimensions in row concatenation for a Frame"

    #Frame to Vec concat (column wise)
    yy = df2[0]
    zz = df3[0]
    hdf = df1.concat([yy,zz])
    rows3, cols3 = hdf.dim
    print(rows3,cols3)
    print(hdf)
    assert rows3 == 10000 and cols3 == 12, "unexpected dimensions in Frame to Vec concatenation"

    #Vec to Vec concat (column wise)
    xx = df1[0]
    yy = df2[0]
    zz = df3[0]
    hdf2 = xx.concat([yy,zz])
    rows4, cols4 = hdf2.dim
    print(rows4,cols4)
    print(hdf2)
    assert rows4 == 10000 and cols4 == 3, "unexpected dimensions in Vec to Vec concatenation"

    #Frame to Vec concat (row wise)
    yy = df2[0,:]
    zz = df3[0,:]
    hdf3 = df1.concat([yy,zz],axis=0)
    rows5, cols5 = hdf3.dim
    print(rows5,cols5)
    print(hdf3)
    assert rows5 == 10002 and cols5 == 10, "unexpected dimensions in Frame to Vec concatenation"

    #Vec to Vec concat (row wise)
    xx = df1[0,:]
    yy = df2[0,:]
    zz = df3[0,:]
    hdf4 = xx.concat([yy,zz],axis=0)
    rows6, cols6 = hdf4.dim
    print(rows6,cols6)
    print(hdf4)
    assert rows6 == 3 and cols6 == 10, "unexpected dimensions in Vec to Vec concatenation"

if __name__ == "__main__":
    pyunit_utils.standalone_test(concat)
else:
    concat()