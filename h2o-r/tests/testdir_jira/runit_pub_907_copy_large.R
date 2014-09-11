##
# Testing logging timeing for copy
# Test for JIRA PUB-907 
# 'Logging time in copy operator'
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')


test <- function(conn) {
    print("Reading in arcene dataset")
        dataset = h2o.uploadFile(conn, locate("smalldata/arcene/arcene_train.data"), key="dataset", header=FALSE)

    print("Time copying of entire datatset")
        startTime = proc.time()
        dataset.copy = h2o.assign(dataset, "dataset.copy")
        endTime = proc.time()

        elapsedTime = endTime - startTime
        print(elapsedTime)

    print("Assert runtime less than 30 seconds")
        stopifnot(elapsedTime < 30)

  testEnd()
}

doTest("Test logging time for copy", test)
