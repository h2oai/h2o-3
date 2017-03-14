from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def pivot():
    df = h2o.create_frame(rows=1000000,
                          cols=3,
                          factors=10,
                          categorical_fraction=1.0/3,
                          time_fraction=1.0/3,
                          real_fraction=1.0/3,
                          real_range=100,
                          missing_fraction=0.0,
                          seed=123)
    dcol = df.moment(year=df["C1"].year(),month=df["C1"].month(),day=df["C1"].day())
    df2 = df.cbind(dcol)
    df2.show()
    df3 = df2.pivot(index="time", column="C2", value="C3")
    df3.show()
    assert len(df3) == 18250
    assert len(df3.columns) == 11
    h2o.remove(df)
    h2o.remove(df2)
    h2o.remove(df3)
    h2o.remove(dcol)


if __name__ == "__main__":
    pyunit_utils.standalone_test(pivot)
else:
    pivot()