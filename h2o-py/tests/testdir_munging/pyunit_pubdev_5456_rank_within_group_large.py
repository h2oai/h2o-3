import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from random import randint
import random


def test_rankWithGroupBy():
    train,groupCols,sortCols = generate_trainingFrame()
    answerFrame, finalDir, finalSorts, sortCols, sortDirs, newColName = generate_answerFrame(train, groupCols, sortCols) # the rank_within_group result should return this
    rankedFrame = train.rank_within_group_by(groupCols, sortCols, sortDirs, newColName)
    rankedFrame.summary()
    pyunit_utils.compare_frames_local_onecolumn_NA(answerFrame[newColName], rankedFrame[newColName],1, tol=1e-10)

def generate_trainingFrame():
    nrows = 1000000 # per nidhi request
    trainGroup = pyunit_utils.random_dataset_enums_only(nrows, randint(1,3), randint(2,10))
    trainEnums = pyunit_utils.random_dataset_numeric_only(nrows, randint(1,3), randint(20, 100))   # columns to sort
    sortColumnsNames = ["sort0", "sort1", "sort2"]
    trainEnums.set_names(sortColumnsNames[0:trainEnums.ncols])
    groupNames = ["GroupByCols0","GroupByCols1","GroupByCols2"]
    trainGroup.set_names(groupNames[0:trainGroup.ncols])
    finalTrain = trainGroup.cbind(trainEnums) # this will be the training frame
    return finalTrain,trainGroup.names,trainEnums.names

def generate_answerFrame(originalFrame, groupByCols, sortCols):
    """
    Given a dataset, a list of groupBy column names or indices and a list of sort column names or indices, this
    function will return a dataframe that is sorted according to the columns in sortCols and a new column is added
    to the frame that indicates the rank of the row within the groupBy columns sorted according to the sortCols.

    :param originalFrame:
    :param groupByCols: 
    :param sortCols:
    :return:
    """
    sortDirs = [True]*len(sortCols)
    for ind in range(len(sortCols)):
        sortDirs[ind] = bool(random.getrandbits(1)) # randomize sort direction

    finalDir = [True]*len(groupByCols)
    finalDir.extend(sortDirs)
    finalSorts = []
    finalSorts.extend(groupByCols)
    finalSorts.extend(sortCols)
    answerFrame = originalFrame.sort(finalSorts, finalDir)
    newColName = "new_rank_within_group"
    nrows = answerFrame.nrow
    newCol = [float('nan')]*nrows

    groupLen = len(groupByCols)
    sortLen = len(sortCols)
    startRank = 1
    keys = ['1']*groupLen
    currKeys = ['1']*groupLen

    sortFrames = answerFrame[sortCols]
    tempS = sortFrames.as_data_frame(use_pandas=False)
    groupFrames = answerFrame[groupByCols]
    tempG = groupFrames.as_data_frame(use_pandas=False)

    for row in range(1,nrows):
        noNAs = True
        if (len(tempS[row])>0):
            for col in range(sortLen):
                strR = tempS[row][col]
                if (len(strR)==0):
                    noNAs = False
                    break   # move on to next row
        else:
            noNAs=False # NAN in one column setting

        if (noNAs):
            if (len(tempG[row]) > 0):   # no NAN
                for colg in range(groupLen):    # read in key of current row
                    currKeys[colg] = tempG[row][colg]
            else:
                currKeys = [""]*groupLen

            if not(currKeys==keys):
                for colg in range(groupLen):    # copy over new key
                    keys[colg] = currKeys[colg]
                startRank=1
            newCol[row-1] = startRank
            startRank=startRank+1

    newColFrame = h2o.H2OFrame(newCol)
    newColFrame.set_names([newColName])
    answerFrame = answerFrame.cbind(newColFrame)

    return answerFrame, finalDir, finalSorts, sortCols, sortDirs, newColName


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_rankWithGroupBy)
else:
    test_rankWithGroupBy()
