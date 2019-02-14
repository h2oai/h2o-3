setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Comparison of H2O to R with varying link functions for the Negative Binomial family on prostate dataset
# Link functions: log (canonical link)
#				  identity
##
library(MASS)

test.linkFunctions <- function() {
  print("Read in prostate data.")
  h2o.data = h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"),
                            destination_frame = "h2o.data")
  R.data = as.data.frame(as.matrix(h2o.data))
  
  print("Testing for family: NEGATIVE BINOMIAL")
  print("Set variables for h2o.")
  myY = "GLEASON"
  myX = c("ID", "AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
  print("Define formula for R")
  R.formula = (R.data[, "GLEASON"] ~ .)
  
  thetaSet = c(0.001, 0.01, 0.1, 0.5, 1)
  
  for (thetaO in thetaSet) {
    thetaO = 0.5
    print("Create models with canonical link: IDENTITY")
    rglm <-
      glm(
        formula = R.formula,
        data = R.data[, 2:9],
        family = negative.binomial(link = identity, theta = thetaO),
        na.action = na.omit
      )

    h2oglm <-
      h2o.glm(
        x = myX,
        y = myY,
        training_frame = h2o.data,
        family = "negativebinomial",
        link = "identity",
        alpha = 0.5,
        lambda = 0,
        nfolds = 0,
        theta = thetaO
      )
    compareModels(h2oglm, rglm)
    
    print("Create models with canonical link: LOG")
    h2oglm <-
      h2o.glm(
        x = myX,
        y = myY,
        training_frame = h2o.data,
        family = "negativebinomial",
        link = "log",
        alpha = 0.5,
        lambda = 0,
        nfolds = 0,
        theta = thetaO
      )
    rglm <-
      glm(
        formula = R.formula,
        data = R.data[, 2:9],
        family = negative.binomial(link = log, theta = thetaO),
        na.action = na.omit
      )
    compareModels(h2oglm, rglm)
  }
}

compareModels <- function(h2oModel, rModel){
  # compare AIC
  print("Compare model deviances for link function log")
  print("H2O GLM model")
  print(h2oModel)
  print("R GLM model")
  print(rModel)
  h2oDeviance = h2oModel@model$training_metrics@metrics$residual_deviance / h2oModel@model$training_metrics@metrics$null_deviance
  rDeviance = deviance(rModel)  / h2oModel@model$training_metrics@metrics$null_deviance
  difference = rDeviance - h2oDeviance
  print(difference)

  if (difference > 0.01) {
    print(cat("Deviance in H2O: ", h2oDeviance))
    print(cat("Deviance in R: ", rDeviance))
    checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
  }
}

doTest("Comparison of H2O to R with varying link functions for the POISSON family", test.linkFunctions)


