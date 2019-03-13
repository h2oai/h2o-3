setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.GLM.covtype <- function() {
  Log.info("Importing covtype.20k.data...\n")
  covtype = h2o.importFile(path=locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] = as.factor(covtype[,55])
  lambdaM <- c(0.001, 0.0001, 0.00001)
  minLogloss1 <- 100
  minLogloss2 <- 100
  trainMetric1 <- NULL
  trainMetric2 <- NULL
  lambda1 <- 0
  lambda2 <- 0

  for (lambda in lambdaM) {
    covtype.h2o1 <- h2o.glm(x=1:54, y=55, training_frame = covtype,
                           family = "multinomial", solver="IRLSM_NATIVE", alpha = 0.5, lambda = lambda)
    logloss1 <- h2o.logloss(covtype.h2o1)
    if (logloss1 < minLogloss1) {
      minLogloss1 <- logloss1
      lambda1 <- lambda
      trainMetric1 <- covtype.h2o1@model$training_metrics
    }
    covtype.h2o2 <- h2o.glm(x=1:54, y=55, training_frame = covtype,
                           family = "multinomial", solver="IRLSM", alpha = 0.5, lambda = lambda)
    logloss2 <- h2o.logloss(covtype.h2o2)
    if (logloss2 < minLogloss2) {
      minLogloss2 <- logloss2
      lambda2 <- lambda
      trainMetric2 <- covtype.h2o2@model$training_metrics
    }
  }
  print("IRLSM_SPEEDUP best training metrics....")
  print(trainMetric1)
  print("IRLSM best training metrics....")
  print(trainMetric2)
  
  print(paste("IRLSM_SPEEDUP best logloss ", minLogloss1, " lambda: ", lambda1))
  print(paste("IRLSM best logloss ", minLogloss2, " lambda: ", lambda2))

  expect_true(minLogloss1 < minLogloss2)
}

doTest("Test GLM on covtype(20k) dataset", test.GLM.covtype)

