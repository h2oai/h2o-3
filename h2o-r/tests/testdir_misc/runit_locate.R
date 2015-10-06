setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test <- function() {

    e <- tryCatch(locate("this_file_does_not_exist.csv"), error = function(x) x)
    expect_true("Could not find the dataset bucket: this_file_does_not_exist.csv" == e[[1]])

    setwd("/")
    e <- tryCatch(locate("this_file_does_not_exist.csv"), error = function(x) x)
    expect_true("evaluation nested too deeply: infinite recursion / options(expressions=)?" == e[[1]])
}

doTest("test", test)

