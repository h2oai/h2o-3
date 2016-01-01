setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.sep <- function() {
  h2o.importFile(h2oTest.locate("smalldata/logreg/prostate.csv"), sep=",")
  
}

h2oTest.doTest("Test the separator gets mapped properly", test.sep)
