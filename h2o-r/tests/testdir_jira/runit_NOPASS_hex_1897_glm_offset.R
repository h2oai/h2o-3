## This test is to check the offset argument for GLM
## The test will import the prostate data set,
## runs glm with and without intecept and create predictions from both models,
## compare the two h2o glm models with a glmnet model ran without offset.

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.GLM.offset <- function(conn) {
  
  compare_res_deviance <- function(h2o_model, r_model){
    diff = h2o_model@model$deviance - r_model$deviance
    if (diff > 0.1) stop('residual deviance is not comparable')
  }
  compare_scores <- function(h2o_model, r_model) {
    pred.r = r_model$fitted.values
    pred.h2o = h2o.predict(h2o_model)
    if (h2o_model@model$params$family$family == "binomial") {
      pred.h2o.r = as.matrix(pred.h2o[,3])[,1]
    } else{
      pred.h2o.r = as.matrix(pred.h2o)[,1]
    }
    
    diff = pred.r - pred.h2o.r
    
    if (TRUE %in% (diff > 2)) stop ('difference between scores not in the prescribed bounds')
  }
  
  Log.info ('Check binomial models for GLM with and without offset')
  Log.info ('Import prostate dataset into H2O and R...')
  prostate.hex = h2o.importFile(conn, system.file("extdata", "prostate.csv", package = "h2o"))
  prostate.csv = as.data.frame(prostate.hex)
  
  family_type = c("binomial", "poisson")
  
  check_models <- function (family_type) {
    Log.info (paste ("Checking", family_type, "models without offset..."))
    prostate.glm.r = glm(formula = CAPSULE ~ . - ID - AGE, family = family_type, data = prostate.csv)
    prostate.glm.h2o = h2o.glm(x = c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"), y = "CAPSULE", training_frame = prostate.hex, family = family_type, standardize = F)
    compare_res_deviance(prostate.glm.h2o, prostate.glm.r)
    compare_scores(prostate.glm.h2o, prostate.glm.r)
    
    Log.info (paste ("Checking", family_type, "models with offset..."))
    options(warn=-1)
    prostate.glm.r = glm(formula = CAPSULE ~ . - ID - AGE, family = family_type, data = prostate.csv, offset = prostate.csv$AGE)
    prostate.glm.h2o = h2o.glm(x = c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"), y = "CAPSULE", training_frame = prostate.hex, family = family_type, offset = "AGE", standardize = F)
    compare_res_deviance(prostate.glm.h2o, prostate.glm.r)
    compare_scores(prostate.glm.h2o, prostate.glm.r)
    print("PASSED")
  }
  
  run_models = sapply(family_type, check_models)
  print(run_models)
  testEnd()
}

doTest("GLM Test: Offset", test.GLM.offset)

