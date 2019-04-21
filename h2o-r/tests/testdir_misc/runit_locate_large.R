setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test <- function() {
    e <- tryCatch(locate("this_file_does_not_exist.csv"), error = function(x) x)
    print(e[[1]])
    expect_true("Could not find the dataset bucket: this_file_does_not_exist.csv" == e[[1]])

    setwd("/")
    e <- tryCatch(locate("this_file_does_not_exist.csv"), error = function(x) x)
    print(e[[1]])
    geterr1 <- "node stack overflow" %in% e[[1]] # new error message depending on R version
    geterr2 <- "evaluation nested too deeply: infinite recursion / options(expressions=)?" %in% e[[1]]
    geterr3 <- all(sapply("too close to the limit", grepl, e[[1]]))
    expect_true(geterr1 || geterr2 || geterr3)
}

doTest("test", test)

