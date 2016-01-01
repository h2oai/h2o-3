setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.parse.false <- function() {
    path <- h2oTest.locate("smalldata/jira/hexdev_29.csv")

    fr <- h2o.importFile(path)
    fraw <- h2o.importFile(path, parse=FALSE)
    fhex <- h2o.parseRaw(fraw)
    expect_equal(nrow(fr), nrow(fhex))
    expect_equal(ncol(fr), ncol(fhex))

    
}

h2oTest.doTest("Parse false", test.parse.false)
