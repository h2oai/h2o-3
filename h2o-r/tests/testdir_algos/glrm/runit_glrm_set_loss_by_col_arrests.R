setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.glrm.check_loss <- function() {
  h2oTest.logInfo("Importing USArrests.csv data...") 
  arrestsR <- read.csv(h2oTest.locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  print(summary(arrestsH2O))
  
  h2oTest.logInfo("Run GLRM with loss by column = L1, Quadratic, Quadratic, Huber")
  fitH2O <- h2o.glrm(training_frame = arrestsH2O, k = 3, loss = "Quadratic", loss_by_col = c("Absolute", "Huber"), loss_by_col_idx = c(0, 3), regularization_x = "None", regularization_y = "None")
  h2oTest.logInfo(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))
  fitY <- as.matrix(fitH2O@model$archetypes)
  fitX <- h2o.getFrame(fitH2O@model$representation_name)
  fitX.mat <- as.matrix(fitX)
  
  h2oTest.logInfo("Check final objective function value")
  fitXY <- fitX.mat %*% fitY
  fitDiff <- arrestsR - fitXY
  objVal <- abs(fitDiff[,1]) + fitDiff[,2]^2 + fitDiff[,3]^2
  objVal <- objVal + sapply(fitDiff[,4], function(x) { ifelse(abs(x) <= 1, x^2/2, abs(x)-0.5) })
  expect_equal(sum(objVal), fitH2O@model$objective)
  
  h2oTest.checkGLRMPredErr(fitH2O, arrestsH2O, tolerance = 1e-6)
  
}

h2oTest.doTest("GLRM Golden Test: USArrests with Loss set by Column", test.glrm.check_loss)
