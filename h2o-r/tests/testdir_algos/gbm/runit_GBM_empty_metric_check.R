setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.emptyModelMetrics <- function() {
  training_file <- random_dataset("regression", testrow = 200)
  allNames = h2o.names(training_file)
  xnames = allNames[-which(allNames=="response")]
  y <- "response"
  test <- training_file[xnames]
  model2 <- h2o.gbm(x=xnames,y=y,training_frame=training_file,ntrees=10,max_depth=3)
  tryCatch(h2o.performance(model2, newdata=test), error =function(x) FAIL("Should not have failed here with empty model metrics message."))
}

doTest("Test Empty Model Metrics", test.emptyModelMetrics)
