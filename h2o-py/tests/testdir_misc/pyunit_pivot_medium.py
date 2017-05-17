#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Pyunit for h2o.pivot"""
from __future__ import print_function
import sys
sys.path.insert(1,"../../")
from builtins import range
import h2o
import pandas
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
    # Pandas comparison
    pdf = df.as_data_frame()
    ppdf = pdf.pivot(values="C3",index="C1",columns="C2")
    ppdf = ppdf.fillna(0.0)
    ppdfh2o = h2o.H2OFrame(ppdf)
    pivoted = df.pivot(index="C1",column="C2",value="C3")
    pivoted.impute(values=[0]*11)
    assert abs((pivoted[:,1:11] - ppdfh2o).sum(return_frame=False)) < 1e-11, "Difference between Pandas pivot too high"


    print("Testing size: ")
    for s in [100,200,300,400,500,1000,2000,4211,100000]:
        print(str(s))
        df1 = h2o.H2OFrame({"index":list(range(1,s+1)),"column":["a"]*s,"value":[1]*s})
        df2 = h2o.H2OFrame({"index":list(range(1,s+1)),"column":["b"]*s,"value":[2]*s})
        df3 = h2o.H2OFrame({"index":list(range(1,s+1)),"column":["c"]*s,"value":[3]*s})
        dfall = df1.rbind(df2)
        dfall = dfall.rbind(df3)
        res = dfall.pivot(index="index", column="column", value="value")
        assert res["a"].sum()[0,0] == 1*s, "Wrong sum of 'a' column"
        assert res["b"].sum()[0,0] == 2*s, "Wrong sum of 'b' column"
        assert res["c"].sum()[0,0] == 3*s, "Wrong sum of 'c' column"
    # See if it can find the last label
    df = h2o.create_frame(rows=1000001,randomize=False,integer_fraction=0.0,categorical_fraction=0.0,time_fraction=0.0,cols=2,value=1.0,missing_fraction=0.0)
    df2 = h2o.create_frame(rows=1000000,integer_fraction=0.0,categorical_fraction=1.0,time_fraction=0.0,cols=1,factors=2,missing_fraction=0.0)
    df2b = df2.rbind(h2o.H2OFrame({"C1":"b"}))
    df2b.set_names(["C3"])
    dft = df.cbind(df2b)
    p = dft.pivot(index="C1",value="C2",column="C3")
    assert len(p.columns) == 4, "Wrong number of columns for last label test"
    assert len(p) == 1, "Wrong number of rows for last label test"


if __name__ == "__main__":
    pyunit_utils.standalone_test(pivot)
else:
    pivot()
