setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

# This test will test the implementation of topN and bottomN.

test.topNbottomN = function() {
    tolerance=1e-12
    dataset <- h2o.uploadFile(locate("bigdata/laptop/jira/TopBottomNRep4.csv.zip"))
    topAnswer = h2o.uploadFile(locate("smalldata/jira/Top20Per.csv.zip"))
    bottomAnswer = h2o.uploadFile(locate("smalldata/jira/Bottom20Per.csv.zip"))
    nPercent = c(1,2,3,4)

    # test topN with randomly chosen N percent, and with column names and column index
    frameNames = names(dataset)
    nP = nPercent[sample(1:length(nPercent),1,replace=F)]
    colIndex = sample(1:length(frameNames),1,replace=F)
    print(paste("test topN: nPercent is", nP, " Column index is ", colIndex))
    topNf = h2o.topN(dataset,frameNames[colIndex],nP)   # call with column name
    topNfI = h2o.topN(dataset,colIndex,nP)              # call with column index

    # result from column name and column index should be the same
    h2o_and_R_equal(topNf, topNfI)
    compare_rep_frames(topAnswer, topNf, tolerance, colIndex,0)

    # test bottomN with randomly chosen N percent, and with column names and column index
    frameNames = names(dataset)
    nP = nPercent[sample(1:length(nPercent),1,replace=F)]
    colIndex = sample(1:length(frameNames),1,replace=F)
    print(paste("test bottomN: nPercent is", nP, " Column index is ", colIndex))
    bottomNf = h2o.bottomN(dataset,frameNames[colIndex],nP)   # call with column name
    bottomNfI = h2o.bottomN(dataset,colIndex,nP)              # call with column index
    # result from column name and column index should be the same
    h2o_and_R_equal(bottomNf, bottomNfI)
    compare_rep_frames(bottomAnswer, bottomNfI, tolerance, colIndex,1)
}

compare_rep_frames = function(answerFrame, actualFrame, tolerance, colIndex, getBottom) {
    answerA = sort(data.matrix(as.data.frame(answerFrame)[colIndex]))
    actualA = sort(data.matrix(as.data.frame(actualFrame)[2]))
    highIndex = nrow(answerFrame)
    distinctValNum = nrow(actualFrame)/4
    allIndex = c((highIndex-distinctValNum+1):highIndex)
    repIndex = 1
    if (getBottom > 0) {
      allIndex = c(1:distinctValNum)
    }
     for (index in allIndex) {
       expect_true(abs(answerA[index]-actualA[repIndex*4])<tolerance)
       repIndex = repIndex+1
     }
}

doTest("Test sort H2OFrame", test.topNbottomN)

