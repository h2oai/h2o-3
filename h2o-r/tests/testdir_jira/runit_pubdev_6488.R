setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.tree.fetch <- function() {
  
  data <- as.h2o(data.frame(
    x1 = c(1, NA, 2),
    x2 = c(2, NA, 1),
    y = c(1, 2,3)
  ))
  
  # A simple model to test prediction the correctly printed by delegating to print.H2ONode.
  model <- h2o.xgboost(x= c("x1", "x2"), y = "y", training_frame = data, max_depth = 0, ntrees = 1)
  tree <- h2o.getModelTree(model, 1)
  expect_false(is.null(tree))
  expect_equal(3, grep("Prediction is 0", capture.output(print(tree@root_node))))
  
}

doTest("Tree API fetch & parse test", test.tree.fetch)
