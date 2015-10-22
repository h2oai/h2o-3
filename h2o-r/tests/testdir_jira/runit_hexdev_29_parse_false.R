setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.parse.false <- function() {
    path <- locate("smalldata/jira/hexdev_29.csv")

    fr <- h2o.importFile(path)
    fraw <- h2o.importFile(path, parse=FALSE)
    fhex <- h2o.parseRaw(fraw)
    expect_equal(nrow(fr), nrow(fhex))
    expect_equal(ncol(fr), ncol(fhex))

    
}

doTest("Parse false", test.parse.false)
