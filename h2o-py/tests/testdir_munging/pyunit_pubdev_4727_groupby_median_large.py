import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import numpy as np
from random import randint

# global dictionaries storing model answers
g_iris_setosa_sepal_len=dict()
g_iris_versicolor_sepal_wid=dict()
g_iris_virginica_petal_wid=dict()
g_iris_versicolor_petal_len_NA_ignore=dict()
g_iris_versicolor_petal_len_NA_rm=dict()


def group_by_all():
    """
    I am testing the groupby median function in PUBDEV_4727.
    """

    # generate random dataset with factor column and real columns
    row_num_max = 100000 # 100000
    row_num_min = 100
    enumLow = 5
    enumHigh = 30
    enumVals = randint(enumLow, enumHigh)   # total number of groupby class
    pIndex = []
    pNum = []
    numpMedian = [] # store python median calculated for each groupby class
    numpMean = []  # store the python mean calculated for each groupby class for column2
    numpMedian2 = []
    numpMean2 = []
    tot = 1e-10
    colFac = 1.1
    for index in range(enumVals):
        rowNum = randint(row_num_min, row_num_max)
        indexList = [index]*rowNum
        numList = np.random.rand(rowNum,1)

        numpMedian.append(list(np.median(numList, axis=0))[0])
        numpMean.append(list(np.mean(numList, axis=0))[0])
        numpMedian2.append(list(np.median(numList*colFac, axis=0))[0])
        numpMean2.append(list(np.mean(numList*colFac, axis=0))[0])

        pIndex.extend(indexList)
        pNum.extend(numList)

    # generate random H2OFrame
    newOrder = np.random.permutation(len(pIndex))
    python_lists = []
    for index in range(len(pIndex)):
        temp = [pIndex[newOrder[index]], pNum[newOrder[index]][0], pNum[newOrder[index]][0]*colFac]
        python_lists.append(temp)
    h2oframe= h2o.H2OFrame(python_obj=python_lists, column_types=["enum","real","real"], column_names=["factors", "numerics", "numerics2"])

    # generate h2o groupby medians and other groupby functions
    groupedMedianF = h2oframe.group_by(["factors"]).median(na='rm').mean(na='all').sum(na="all").count(na="rm").get_frame()

    groupbyMedian = [0]*len(numpMedian) # extract groupby median to compare with python median
    groupbyMean = [0]*len(numpMean)
    groupbyMedian2 = [0]*len(numpMedian2) # extract groupby median to compare with python median
    groupbyMean2 = [0]*len(numpMean2)
    for rowIndex in range(enumVals):
        groupbyMedian[int(groupedMedianF[rowIndex,0])] = groupedMedianF[rowIndex,'median_numerics']
        groupbyMean[int(groupedMedianF[rowIndex,0])] = groupedMedianF[rowIndex,'mean_numerics']
        groupbyMedian2[int(groupedMedianF[rowIndex,0])] = groupedMedianF[rowIndex,'median_numerics2']
        groupbyMean2[int(groupedMedianF[rowIndex,0])] = groupedMedianF[rowIndex,'mean_numerics2']

    # print out groupby/numpy median and mean
    print(groupedMedianF.as_data_frame(use_pandas=True, header=False))
    print("H2O Groupby median is for numerics {0}".format(groupbyMedian))
    print("Numpy median is numerics {0}".format(numpMedian))
    print("H2O Groupby median is for numerics {0}".format(groupbyMedian))
    print("Numpy median is numerics {0}".format(numpMedian))
    print("H2O Groupby mean is for numerics2 {0}".format(groupbyMean2))
    print("Numpy mean is numerics2 {0}".format(numpMean2))
    print("H2O Groupby mean is for numerics2 {0}".format(groupbyMean2))
    print("Numpy mean is numerics2 {0}".format(numpMean2))

    # compare the h2o groupby medians, means with numpy medians and means.
    assert pyunit_utils.equal_two_arrays(groupbyMedian, numpMedian, tot, tot), "H2O groupby median and numpy " \
                                                                                   "median is different."
    assert pyunit_utils.equal_two_arrays(groupbyMean, numpMean, tot, tot), "H2O groupby mean and numpy " \
                                                                                   "mean is different."
    assert pyunit_utils.equal_two_arrays(groupbyMedian2, numpMedian2, tot, tot), "H2O groupby median and numpy " \
                                                                                   "median is different."
    assert pyunit_utils.equal_two_arrays(groupbyMean2, numpMean2, tot, tot), "H2O groupby mean and numpy " \
                                                                            "mean is different."

if __name__ == "__main__":
    pyunit_utils.standalone_test(group_by_all)
else:
    group_by_all()
