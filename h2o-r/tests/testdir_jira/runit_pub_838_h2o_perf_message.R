setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# The following bug is associated with JIRA PUB-838
# 'Inaccurate error message: h2o.performance()'
# Testing h2o.performance with rogue label vector and original dataframe
##





test <- function() {
  print("Reading in original prostate data.")
  prostate.hex <- h2o.importFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex", header=TRUE)

  print("Run test/train split at 20/80.")
  prostate.hex$split <- ifelse(h2o.runif(prostate.hex)>0.8, yes=1, no=0)
  prostate.train <- h2o.assign(prostate.hex[prostate.hex$split == 0, c(1:9)], "prostate.train")
  prostate.test <- h2o.assign(prostate.hex[prostate.hex$split == 1, c(1:9)], "prostate.test")
  test.labels <- h2o.assign(prostate.test[,2], "test.labels")

  print("Set variables to build models")
  myX <- c(3:9)
  myY <- 2

  print("Creating model")
  system.time(h2o.glm.model <- h2o.glm(x=myX, y=myY, training_frame=prostate.train, model_id="h2o.glm.prostate", family="binomial", alpha=1, lambda_search=F, nfolds=0))

  print("Predict on test data")
  prediction <- predict(h2o.glm.model, prostate.test)

  print("Check performance of model")
  h2o.performance(h2o.glm.model, prostate.test) # works
  h2o.performance(h2o.glm.model, test.labels) # checking performance with separate vector containing labels

  
}

h2oTest.doTest("Testing h2o.performance with rogue label vector and original dataframe ", test)
