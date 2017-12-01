setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.gbm <- function() {
  # Determine model sizes
  seeds = round(runif(1,min=1,max=1000000000000))
 # seeds = c(987654321, 123456789, 1029384756)
  ntree=50
  
  auc_run1 <- TrainGBM(seeds, ntree)
  auc_run2 <- TrainGBM(seeds, ntree)
  expect_equal(auc_run1, auc_run2)
}

# borrowed from Megan K
TrainGBM <- function(seedNum, nt) {
  data <- h2o.importFile(locate("bigdata/laptop/jira/reproducibility_issue.csv.zip"))
  gbm_v1 <- h2o.gbm(x=2:365, y='response', training_frame = data,
          distribution = "bernoulli", ntrees = nt, seed = seedNum, max_depth = 4, min_rows = 7,
          score_tree_interval=nt
  )
  auc_gbm = gbm_v1@model$training_metrics@metrics$thresholds_and_metric_scores$threshold
  h2o.rm(data)
  h2o.rm(gbm_v1)
  auc_gbm
}

doTest("GBM reproducibility test", test.gbm)
