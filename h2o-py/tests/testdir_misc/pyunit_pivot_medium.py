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
    h2o.remove_all()
    print("Testing size: ")
    for s in [100,200,300,400,500,1000,2000,4211,100000]:
        print(str(s))
        df1 = h2o.H2OFrame({"index":range(1,s+1),"column":["a"]*s,"value":[1]*s})
        df2 = h2o.H2OFrame({"index":range(1,s+1),"column":["b"]*s,"value":[2]*s})
        df3 = h2o.H2OFrame({"index":range(1,s+1),"column":["c"]*s,"value":[3]*s})
        dfall = df1.rbind(df2)
        dfall = dfall.rbind(df3)
        res = dfall.pivot(index="index", column="column", value="value")
        assert res["a"].sum()[0,0] == 1*s
        assert res["b"].sum()[0,0] == 2*s
        assert res["c"].sum()[0,0] == 3*s
        h2o.remove_all() 

if __name__ == "__main__":
    pyunit_utils.standalone_test(pivot)
else:
    pivot()
