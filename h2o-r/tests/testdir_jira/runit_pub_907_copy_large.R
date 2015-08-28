##
# Testing logging timeing for copy
# Test for JIRA PUB-907 
# 'Logging time in copy operator'
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')


test <- function(conn) {
    print("Reading in arcene dataset")
        dataset = h2o.importFile(locate("smalldata/arcene/arcene_train.data"), destination_frame="dataset", header=FALSE)

    print("Time copying of entire datatset")
        startTime = proc.time()
        dataset.copy = h2o.assign(dataset, "dataset.copy")
        endTime = proc.time()

        elapsedTime = endTime - startTime
        print(elapsedTime)

    print("Assert runtime less than 180 seconds")
        stopifnot(elapsedTime < 180)  # should finish in less than three minutes.

  testEnd()
}

doTest("Test logging time for copy", test)
