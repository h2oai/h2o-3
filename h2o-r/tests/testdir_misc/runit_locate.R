setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test <- function() {

    e <- tryCatch(h2oTest.locate("this_file_does_not_exist.csv"), error = function(x) x)
    expect_true("Could not find the dataset bucket: this_file_does_not_exist.csv" == e[[1]])

    setwd("/")
    e <- tryCatch(h2oTest.locate("this_file_does_not_exist.csv"), error = function(x) x)
    expect_true("evaluation nested too deeply: infinite recursion / options(expressions=)?" == e[[1]])
}

h2oTest.doTest("test", test)

