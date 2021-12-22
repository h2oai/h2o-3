setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test <- function() {
  # generate random dataset to test our merging
  alldata <- genDataFrames(10000, 1000, 2147483648, 10)
  
  # perform tests
  performMergingTest(alldata$dataframeA, alldata$dataframeB, alldata$frameA, alldata$frameB, method="radix", testNo=1) # test radix sort with all.x=all.y=FALSE
  performMergingTest(alldata$dataframeA, alldata$dataframeB, alldata$frameA, alldata$frameB, method="radix", testNo=2) # test radix sort with all.x=TRUE, all.y=FALSE
  performMergingTest(alldata$dataframeA, alldata$dataframeB, alldata$frameA, alldata$frameB, method="radix", testNo=3) # test radix sort with all.x=FALSE, all.y=TRUE
}

genDataFrames <- function(nrow, intrange1, intrange2, numRepeats) {
  # generate random dataset to test our merging
  frameA <- h2o.cbind( h2o.createFrame(rows=nrow, cols=1, categorical_fraction=0, integer_fraction=1,
                                       integer_range=intrange1, binary_fraction=0, binary_ones_fraction = 0, time_fraction = 0, string_fraction = 0, missing_fraction=0),
                       h2o.createFrame(rows=nrow, cols=1, categorical_fraction=0, integer_fraction=1,
                                       binary_fraction=0, binary_ones_fraction = 0, time_fraction = 0, string_fraction = 0,
                                       integer_range=intrange2, missing_fraction=0))
  frameB <- h2o.cbind( h2o.createFrame(rows=nrow, cols=1, categorical_fraction=0, integer_fraction=1,
                                       integer_range=intrange1, binary_fraction=0, binary_ones_fraction = 0, time_fraction = 0, string_fraction = 0, missing_fraction=0),
                       h2o.createFrame(rows=nrow, cols=1, categorical_fraction=0, integer_fraction=1,
                                       binary_fraction=0, binary_ones_fraction = 0, time_fraction = 0, string_fraction = 0,
                                       integer_range=intrange2, missing_fraction=0))
  # repeat a frameA row random number of times in frameB to create repeated rows
  numRep = sample(1:numRepeats,1)
  for (ind in c(1:numRep)) {
    frameB[ind,1] = frameA[1,1]
    frameB[ind,2] = frameA[1,2]
  }
  # repeat a frame B row random number of times in frameA to create repeated rows
  numRepB = sample(numRep+1:numRep+numRepeats,1)
  for (ind in c(1:numRepB)) {
    frameA[ind+numRepB,1] = frameB[numRepB,1]
    frameA[ind+numRepB,2] = frameB[numRepB,2]
  }
  names(frameA) <- c("A", "X")
  names(frameB) <- c("A", "Y")
  frameAData <- as.data.frame(frameA)
  frameBData <- as.data.frame(frameB)
  return(list("frameA"=frameA, "frameB"=frameB, "dataframeA"=frameAData, "dataframeB"=frameBData))
}
performMergingTest <- function(frameAData, frameBData, frameA, frameB, methodS, testNo=1) {
  if (testNo==1) {
    fMergeAll <- h2o.arrange(h2o.merge(frameA, frameB,  method=methodS), "A", "X", "Y") # test radix, allRite and compare with R result
    fMergeAllAns <- h2o.arrange(as.h2o(merge(frameAData, frameBData)), "A", "X", "Y")
    checkResults(fMergeAll, fMergeAllAns)
    return
  } 
  if (testNo==2) {
    fMergeAll <- h2o.arrange(h2o.merge(frameA, frameB, all.x=TRUE,  method=methodS), "A", "X", "Y") # test radix, allRite and compare with R result
    fMergeAllAns <- h2o.arrange(as.h2o(merge(frameAData, frameBData, all.x=TRUE)), "A", "X", "Y")
    checkResults(fMergeAll, fMergeAllAns)
    return
  } 
  if (testNo ==3) {
    fMergeAll <- h2o.arrange(h2o.merge(frameA, frameB, all.y=TRUE, method=methodS), "A", "X", "Y") # test radix, allRite and compare with R result
    fMergeAllAns <- h2o.arrange(as.h2o(merge(frameAData, frameBData, all.y=TRUE)), "A", "X", "Y")
    checkResults(fMergeAll, fMergeAllAns)
    return
  }
}

checkResults <- function(frame1, frame2) {
  expect_equal(as.data.frame(frame1), as.data.frame(frame2))
  h2o.rm(frame1)
  h2o.rm(frame2)
}

doTest("PUBDEV-3567", test)
