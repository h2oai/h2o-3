setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
check_strict <- function() {

  expect_true(formals(h2o.init)$strict_version_check)
}
h2oTest.doTest("Check that strict version checking is on.", check_strict)
