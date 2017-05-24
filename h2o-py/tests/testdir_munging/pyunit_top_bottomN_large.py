from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from random import randint
import numpy as np

def h2o_H2OFrame_top_bottomN():
    """
    PUBDEV-3624 Top or Bottom N test h2o.frame.H2OFrame.topN() and h2o.frame.H2OFrame.bottomN() functions.
    Given a H2O frame, a column index or column name, a double denoting percentages of top/bottom rows to 
    return, the topN will return a H2OFrame containing two columns, one will
    be the topN (or bottomN) values of the specified column.  The other column will record the row indices into
    the original frame of where the topN (bottomN) values come from.  This will let the users to grab those
    corresponding rows to do whatever they want with it.
    """
    dataFrame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/jira/TopBottomNRep4.csv.zip"))
    topAnswer = h2o.import_file(pyunit_utils.locate("smalldata/jira/Top20Per.csv.zip"))
    bottomAnswer = h2o.import_file(pyunit_utils.locate("smalldata/jira/Bottom20Per.csv.zip"))
    nPercentages = [1,2,3,4]  # multiples of 4 since dataset is repeated 4 times.
    frameNames = dataFrame.names    # get data column names
    tolerance=1e-12
    nsample = 100

    nP = nPercentages[randint(0, len(nPercentages)-1)]  # pick a random percentage
    colIndex = randint(0, len(frameNames)-1)    # pick a random column
    print("For topN: Percentage chosen is {0}.  Column index chosen is {1}".format(nP, colIndex))
    newTopFrame = dataFrame.topN(frameNames[colIndex], nP)  # call topN with column names
    newTopFrameC = dataFrame.topN(colIndex, nP)             # call topN with same column index

    # the two return frames should be the same for this case, compare 1000 rows chosen randomly
    pyunit_utils.compare_frames(newTopFrame, newTopFrameC, nsample, tol_numeric=tolerance)

    # compare one of the return frames with known answer
    compare_rep_frames(topAnswer, newTopFrame, tolerance, colIndex, 0)

    # test bottomN here
    nP = nPercentages[randint(0, len(nPercentages)-1)]  # pick a random percentage
    colIndex = randint(0, len(frameNames)-1)    # pick a random column
    print("For bottomN: Percentage chosen is {0}.  Column index chosen is {1}".format(nP, colIndex))
    newBottomFrame = dataFrame.bottomN(frameNames[colIndex], nP)  # call topN with column names
    newBottomFrameC = dataFrame.bottomN(colIndex, nP)             # call topN with same column index

    # the two return frames should be the same for this case
    pyunit_utils.compare_frames(newBottomFrame, newBottomFrameC, nsample, tol_numeric=tolerance)
    # compare one of the return frames with known answer
    compare_rep_frames(bottomAnswer, newBottomFrame, tolerance, colIndex, 1)


def compare_rep_frames(answerF, repFrame, tolerance,  colIndex, getBottom=0):
    # actual answer is in second column of repFrame
    highIndex = int(round(repFrame.nrow/4))
    allIndex = range(answerF.nrow-highIndex, answerF.nrow)
    if getBottom>0:     # get bottom N percent
        allIndex = range(0, highIndex)
    repIndex = 0
    answerArray = np.transpose(answerF[colIndex].as_data_frame(header=False).values)[0]
    topBottomArray = np.transpose(repFrame[1].as_data_frame(header=False).values)[0]

    answerArray=np.sort(answerArray)
    topBottomArray=np.sort(topBottomArray)

    for ind in allIndex:
        assert abs(answerArray[ind]-topBottomArray[repIndex*4]) < tolerance, \
            "Expected {0}, Actual {1} .".format(answerArray[ind],topBottomArray[repIndex*4])
        repIndex=repIndex+1

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_top_bottomN)
else:
    h2o_H2OFrame_top_bottomN()


