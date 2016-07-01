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

    df123 = df1.concat([df1,df2,df3])
    rows, cols = df123.dim
    print(rows,cols)
    print(df123)
    assert rows == 10000 and cols == 30, "unexpected dimensions in original"

if __name__ == "__main__":
    pyunit_utils.standalone_test(concat)
else:
    concat()