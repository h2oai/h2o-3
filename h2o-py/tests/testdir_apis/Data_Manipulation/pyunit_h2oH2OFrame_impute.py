from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from random import randrange
import numpy as np
from scipy.stats import mode


def h2o_H2OFrame_impute():
    """
    Python API test: h2o.frame.H2OFrame.impute(column=-1, method='mean', combine_method='interpolate', by=None,
    group_by_frame=None, values=None)
    """
    python_lists = np.random.randint(-5,5, (100,3))
    h2oframe = h2o.H2OFrame(python_obj=python_lists, column_types=["int", "int", "enum"])
    row_ind_mean = randrange(0,h2oframe.nrow)       # row and col index that we want to set to NA and impute with mean
    row_ind_median = randrange(0,h2oframe.nrow)      # row and col index that we want to set to NA and impute with median
    row_ind_mode = randrange(0,h2oframe.nrow)       # row and col index that we want to set to NA and impute with mode

    col0 = list(python_lists[:,0])
    col1 = list(python_lists[:,1])
    col2 = list(python_lists[:,2])

    print(col0)
    print(col1)
    print(col2)

    del col0[row_ind_mean]
    impute_mean = np.mean(col0)
    del col1[row_ind_median]
    impute_median = np.median(col1)
    del col2[row_ind_mode]
    impute_mode = mode(col2).__getitem__(0)[0]
    modeNum = findModeNumber(col2)

    print("first column NA row is {0}, second column NA row is {1}, third column NA row "
          "is {2}".format(row_ind_mean, row_ind_median, row_ind_mode))
    sys.stdout.flush()
    h2oframe[row_ind_mean, 0]=float("nan")      # insert nans into frame
    h2oframe[row_ind_median, 1]=float("nan")
    h2oframe[row_ind_mode, 2]=float("nan")

    h2oframe.impute(column=0, method='mean', by=None, group_by_frame=None, values=None)
    h2oframe.impute(column=1, method='median', combine_method='average', group_by_frame=None, values=None)
    h2oframe.impute(column=2, method='mode')

    # check to make sure correct methods are imputed
    assert abs(h2oframe[row_ind_mean, 0]-impute_mean) < 1e-6, "h2o.H2OFrame.impute() command is not working and " \
                                                              "the difference is {0}".format(abs(h2oframe[row_ind_mean, 0]-impute_mean))
    assert abs(h2oframe[row_ind_median, 1]-impute_median) < 1e-6, "h2o.H2OFrame.impute() command is not working and " \
                                                                  "the difference is {0}.".format(abs(h2oframe[row_ind_median, 1]-impute_median))
    if modeNum == 1:    # python and h2o provide different numbers when there is more than 1 mode
        assert abs(int(h2oframe[row_ind_mode, 2])-impute_mode) < 1e-6, "h2o.H2OFrame.impute() command is not working and " \
                                                                   "the difference is {0}.".format(abs(int(h2oframe[row_ind_mode, 2])-impute_mode))
    else:
        print("impute with mode is not tested here because there are more than one mode found.")

def findModeNumber(python_list):
    countVal = dict()

    for ele in python_list:
        if (ele in countVal.keys()):
            countVal[ele] += 1
        else:
            countVal[ele] = 1

    presentNum = countVal.values()
    maxVal = max(presentNum)
    maxNum = 0;

    for ele in presentNum:
        if ele == maxVal:
            maxNum += 1

    return maxNum


pyunit_utils.standalone_test(h2o_H2OFrame_impute)
