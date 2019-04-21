setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# PUBDEV-5005: GBM residual deviance
# A customer complaint that he is not able to extract the residual deviance after training a GBM model.
# The reason for this is because we use different names for the residual deviance.
#
# 1, For the final model deviance, you can obtained it by calling gbmModel@model$training_metric@metrics$mean_residual_deviance
# and setting.
# 2. If you want to see the history of mean residual deviance, you need to access the training_deviance field of
#  the scoring history
gbm.test <- function() {
  data = h2o.importFile(locate("smalldata/airlines/AirlinesTest.csv.zip"),destination_frame = "data")
  gg = h2o.gbm(x = c(1:4,6:9),y = 5,training_frame = data)
  mrd = gg@model$training_metric@metrics$mean_residual_deviance # last mean residual deviance
  mrd_history = gg@model$scoring_history$training_deviance  # history of mean residual deviance
  print("Model mean residual deviance is ")
  print(mrd)
  print("History of mean residual deivance is ")
  print(mrd_history)
  
  expect_true(abs(mrd-h2o.mean_residual_deviance(gg)) < 1e-12)
}

doTest("GBM Grid Test: Airlines Smalldata", gbm.test)
