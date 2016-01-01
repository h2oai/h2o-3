setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.headers <- function() {
    path <- h2oTest.locate("smalldata/jira/hexdev_29.csv")

    fhex_header_true <- h2o.importFile(path, header=TRUE)
    fhex_header_false <- h2o.importFile(path, header=FALSE)
    fhex_header_unspecified <- h2o.importFile(path)

    expect_error(h2o.importFile(path, header=2))
    expect_true(nrow(fhex_header_true) == nrow(fhex_header_false) - 1)
    expect_true(nrow(fhex_header_unspecified) == nrow(fhex_header_false) || nrow(fhex_header_unspecified) == nrow(fhex_header_true))

    
}

h2oTest.doTest("Header options", test.headers)
