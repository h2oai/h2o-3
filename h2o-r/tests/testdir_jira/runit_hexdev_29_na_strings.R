setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.na.strings <- function() {
    path <- locate("smalldata/jira/hexdev_29.csv")

    fhex <- h2o.importFile(path)
    expect_equal(sum(sapply(1:ncol(fhex), function (c) sum(is.na(fhex[,c])))), 0)

    fhex_na_strings <- h2o.importFile(path, na.strings=list(NULL, c("fish"), NULL))
    expect_equal(sum(sapply(1:ncol(fhex_na_strings), function (c) sum(is.na(fhex_na_strings[,c])))), 2)

    
}

doTest("NA strings", test.na.strings)
