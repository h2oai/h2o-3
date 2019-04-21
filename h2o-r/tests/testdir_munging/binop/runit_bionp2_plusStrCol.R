setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



colPlus.string <- function() {
    expect_error(
        nrow(as.character(as.h2o("A")) + as.character(as.h2o("B"))),
        ".*unimplemented: Binary operation '\\+' is not supported on String columns"
    )
}

doTest("Plus operation on String columns doesn't crush the server", colPlus.string)

