setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing logging timeing for copy
# Test for JIRA PUB-907 
# 'Logging time in copy operator'
##






test <- function() {
    print("Reading in arcene dataset")
        dataset = h2o.importFile(h2oTest.locate("smalldata/arcene/arcene_train.data"), destination_frame="dataset", header=FALSE)

    print("Time copying of entire datatset")
        startTime = proc.time()
        dataset.copy = h2o.assign(dataset, "dataset.copy")
        endTime = proc.time()

        elapsedTime = endTime['elapsed'] - startTime['elapsed']
        print(elapsedTime)

    print("Assert runtime less than 180 seconds")
        stopifnot(elapsedTime < 180)  # should finish in less than three minutes.

  
}

h2oTest.doTest("Test logging time for copy", test)
