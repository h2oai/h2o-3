setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
## This test is to check the offset argument for GLM
## The test will import the prostate data set,
## runs glm with and without intecept and create predictions from both models,
## compare the two h2o glm models with a glmnet model ran without offset.




test.GLM.offset <- function() {

  compare_scores <- function(h2o_model, r_model, data) {
    pred.r <- r_model$fitted.values
    pred.h2o <- h2o.predict(h2o_model, newdata = data)
    if (inherits(h2o_model, "H2OBinomialModel")) {
      pred.h2o.r <- as.matrix(pred.h2o[,3])[,1]
    } else{
      pred.h2o.r <- as.matrix(pred.h2o)[,1]
    }

    diff <- pred.r - pred.h2o.r

    if (TRUE %in% (diff > 2)) stop ('difference between scores not in the prescribed bounds')
  }

  h2oTest.logInfo('Check binomial models for GLM with and without offset')
  h2oTest.logInfo('Import prostate dataset into H2O and R...')
  prostate.hex <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  prostate.csv <- as.data.frame(prostate.hex)

  family_type <- c("binomial", "poisson")

  check_models <- function (family_type) {
    h2oTest.logInfo(paste ("Checking", family_type, "models without offset..."))
    prostate.glm.r <- glm(formula = CAPSULE ~ . - ID - AGE, family = family_type, data = prostate.csv)
    prostate.glm.h2o <- h2o.glm(x = c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"),
      y = "CAPSULE", training_frame = prostate.hex, family = family_type, standardize = F)
    print(paste("h2o residual:", h2o.residual_deviance(prostate.glm.h2o)))
    print(paste("  r residual:",prostate.glm.r$deviance))
    expect_equal(h2o.residual_deviance(prostate.glm.h2o), prostate.glm.r$deviance, tolerance = 0.1,
      label = paste(family_type, "prostate.glm.h2o residual without offsets"))
    compare_scores(prostate.glm.h2o, prostate.glm.r, prostate.hex)

    h2oTest.logInfo(paste ("Checking", family_type, "models with offset..."))
    options(warn=-1)
    prostate.glm.r <- glm(formula = CAPSULE ~ . - ID - AGE, family = family_type, data = prostate.csv, offset = prostate.csv$AGE)
    prostate.glm.h2o <- h2o.glm(x = c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"),
      y = "CAPSULE", training_frame = prostate.hex, family = family_type, offset = "AGE",
      standardize = F)
    print(paste("h2o residual:", h2o.residual_deviance(prostate.glm.h2o)))
    print(paste("  r residual:",prostate.glm.r$deviance))
    expect_equal(h2o.residual_deviance(prostate.glm.h2o), prostate.glm.r$deviance, tolerance = 0.1,
      label = paste(family_type, "prostate.glm.h2o residual with offsets"))
    compare_scores(prostate.glm.h2o, prostate.glm.r, prostate.hex)
  }

  run_models <- sapply(family_type, check_models)
  
}

h2oTest.doTest("GLM Test: Offset", test.GLM.offset)

