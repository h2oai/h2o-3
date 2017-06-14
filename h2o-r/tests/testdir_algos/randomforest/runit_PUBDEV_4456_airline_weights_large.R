setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test does not have an assert at the end and it is an okay.  In PUBDEV-4456, Nidhi noticed that when
# we have a weight column, this test will fail.  This is caused by the assertion error of fs[1]>=0 && fs[1]>=1.
# This failure manisfested as: When fs[1] is 1, it is read back as 1.0000000000000002. To get around this error,
# I just added a tolerance at the end of the bound and changed the assertion to:
#     assert(fs[1]>=-1e-12 && fs[1] <= 1+1e-12.
# This solves the problem for now.
#
# I filed a JIRA PUBDEV-4142 to tackle this problem.  Once this JIRA is completed, I will go in and remove the hack that
# I put in.

test.rf.dataset.weights<- function() {
  data1 = h2o.importFile(locate("smalldata/airlines/modified_airlines.csv"))
  ss = h2o.splitFrame(data1,ratios = c(.7),destination_frames = c("tr","va"),seed = 1)

  rfFit.h2o <- h2o.randomForest(x=1:10,
  y=31,
  ntrees = 1000,
  training_frame=ss[[1]],
  validation_frame=ss[[2]],
  seed = 12345,
  weights_column = "weight", # If weighting applies, include it in training frame and validation frame
  score_tree_interval = 10,
  stopping_rounds = 20,
  stopping_metric = "AUC",
  stopping_tolerance = 0.001,
  max_runtime_secs = 20*60)
}

doTest("rf: dataset with weights", test.rf.dataset.weights)
