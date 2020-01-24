#!/usr/bin/python
# -*- encoding: utf-8 -*-
import h2o
import math
from h2o.exceptions import H2OTypeError, H2OValueError
from tests import pyunit_utils


def test_rbind_summary():
    h2o.remove_all()
    df = h2o.H2OFrame([1, 2, 5.5], destination_frame="df") # original frame
    dfr = h2o.H2OFrame([5.5, 1, 2], destination_frame="dfr") # reversed row content
    df1 = df[2, :]
    df2 = df[:2, :]
    summary = df1.summary(return_data=True)
    df3 = df1.rbind(df2) # fixed
    df3r = df2.rbind(df1)

    compareFramesLocal(dfr, df3) # should contain 5.5, 1, 2
    compareFramesLocal(df, df3r) # should contain 1,2,5.5
    
    df1 = df[3,:] # this will result in an NA since we do not have 4 rows in df.
    dfr[0,0] = float('nan')
    df4 = df1.rbind(df2)
    compareFramesLocal(df4, dfr) # should contain NA, 1, 2

# performing the same test with an additionl categorical column per Michalk request.
    h2o.remove_all()
    df = h2o.H2OFrame([[1,"a"],[2,"b"],[5.5,"c"]],destination_frame="dfc") # original frame
    df[1]=df[1].asfactor()
    dfr = h2o.H2OFrame([[5.5,"c"], [1,"a"], [2,"b"]],destination_frame="dfrc") # reversed row content
    dfr[1] = df[1].asfactor() # this somehow switch the row content of the factor column to be alphabetical
    dfr[0,1]='c'
    dfr[1,1]='a'
    dfr[2,1]='b'
    df1 = df[2, :]
    df2 = df[:2, :]
    summary = df1.summary(return_data=True)
    df3 = df1.rbind(df2) # fixed
    df3r = df2.rbind(df1)
    compareFramesLocal(dfr, df3) # should contain 5.5, 1, 2
    compareFramesLocal(df, df3r) # should contain 1,2,5.5
    
    # copying test from Michalk
    df1 = h2o.H2OFrame([[1,"a"],[2,"b"]])
    df1[1]=df1[1].asfactor()

    df2 = h2o.H2OFrame([[2.2,"b"],[1.1,"a"]])
    df2[1]=df2[1].asfactor()

    print(df1.summary())
    print(df2.summary())

    df3 = df1.rbind(df2)
    assert df3.nrow==(df1.nrow+df2.nrow), "Expected rbind rows: {0}, actual rows: " \
                                          "{1}".format(df1.nrow+df2.nrow, df3.nrow)   
 
# I am having problems with as_data_frame.  Hence using my own function here
def compareFramesLocal(f1, f2):
    ncol = f1.ncol
    nrow = f1.nrow
    
    for cind in range(ncol):
        f1[cind] = f1[cind].asnumeric()
        f2[cind] = f2[cind].asnumeric()       
        for rind in range(nrow):
            temp1 = f1[rind, cind]
            temp2 = f2[rind, cind]
            if not(math.isnan(temp1) and math.isnan(temp2)):
                assert temp1 == temp2, "Frame contents are row {0}, col {1} are different.  Frame 1: {2}.  Frame 2:" \
                                       " {3}".format(rind, cind, f1[rind, cind], f2[rind, cind])
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_rbind_summary)
else:
    test_rbind_summary()
