setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

#Test to see if you apply H2O uniop methods to an H2OFrame it will not overwrite the original column headers & domains
# & also update colnames of new frame with `op(colName)` where `op` is a uniop operator.

test.uniop <- function(){
    #Set up H2OFrame
    fr <- h2o.createFrame(rows = 100, cols = 5, categorical_fraction = 0,
    factors = 0, integer_fraction = 1.0, integer_range = 100,
    binary_fraction = 0, time_fraction = 0, string_fraction = 0,
    has_response = FALSE,seed = 123456789)

    #Conduct a log transform on entire frame
    fr2 = log(fr)

    #Check colnames of original frame remain untouched.
    expect_equal(colnames(fr),c("C1","C2","C3","C4","C5"))

    #Check new colnames for modified frame are of the convention `op(colname)`
    expect_equal(paste0("log(",colnames(fr),")"),colnames(fr2))
}

doTest("Test new naming convention of uniop frames", test.uniop)