setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.nacnt <- function() {
  fr <- h2o.importFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
  nacnts1 <- h2o.nacnt(fr)
  expect_true(all(nacnts1==0))
  h2o.insertMissingValues(fr)
  nacnts2 <- h2o.nacnt(fr)
  expect_true(all(nacnts2>0))
}

h2oTest.doTest("Test nacnt", test.nacnt)
