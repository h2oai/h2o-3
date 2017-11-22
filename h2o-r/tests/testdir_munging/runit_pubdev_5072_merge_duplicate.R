setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test out the merge() functionality
##

test.merge <- function() {
  test3 <- h2o.importFile(locate("smalldata/jira/test3.csv"), header=TRUE)
  test4 <- h2o.importFile(locate("smalldata/jira/test4.csv"), header=TRUE)
  ans <- as.factor(h2o.importFile(locate("smalldata/jira/pubdev_5072_answer.csv"), header=TRUE))
  h2omerge <- h2o.merge(h2o.asfactor(test3), h2o.asfactor(test4), by = "NAME", all.x = TRUE)
  
  # check and make sure the frames are the same
  all.equal(as.data.frame(ans), as.data.frame(h2omerge))
}

doTest("Test out the merge() functionality", test.merge)
