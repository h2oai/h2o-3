from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

import pandas as pd
import numpy as np

def difflag1():
    #Make random pandas frame with 1,000,000 rows ranging from 0-100
    df = pd.DataFrame(np.random.randint(0,100,size=(1000000, 1)), columns=list('A'))
    #Take diff of pandas frame
    df_diff = df.diff()
    #Make into h2o frame for comparison later
    df_diff_h2o = h2o.H2OFrame(df_diff)

    #Convert pandas dataframe to H2OFrame
    fr = h2o.H2OFrame(df)
    #Take diff of H2O frame
    fr_diff = fr.difflag1()

    #Get diff of pandas diff and h2o's diff
    diff = abs(df_diff_h2o[1:df_diff_h2o.nrow,:] - fr_diff[1:fr_diff.nrow,:])

    #Assert that max of diff is less than 1e-10
    assert diff.max() < 1e-10, "expected equal differencing"

if __name__ == "__main__":
    pyunit_utils.standalone_test(difflag1)
else:
    difflag1()