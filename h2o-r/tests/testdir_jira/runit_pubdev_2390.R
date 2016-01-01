setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.pubdev.2390 <- function() {

    fr <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files"),
                         col.types=list(by.col.name=c("C5"),types=c("String")))

    expect_false(is.numeric(fr$C5))
    expect_false(is.factor(fr$C5))
    expect_true(is.character(fr$C5))
}

h2oTest.doTest("PUBDEV-2390: is.character on String type column should return true", test.pubdev.2390)
