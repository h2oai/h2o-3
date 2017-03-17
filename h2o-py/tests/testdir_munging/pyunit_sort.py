from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def sort():
    df = h2o.create_frame(rows=10,
                          cols=3,
                          factors=10,
                          categorical_fraction=1.0/3,
                          time_fraction=1.0/3,
                          real_fraction=1.0/3,
                          real_range=100,
                          missing_fraction=0.0,
                          seed=123)
    df1 = df.sort("C1")
    assert df1[0,0] == 433225652950 # 1983-09-24 04:27:32
    assert df1[9,0] == 1532907020199 # 2018-07-29 23:30:20
    df2 = df.sort("C2")
    assert df2[0,1] == "c1.l1"
    assert df2[9,1] == "c1.l9"
    h2o.remove_all()

if __name__ == "__main__":
    pyunit_utils.standalone_test(sort)
else:
    sort()

