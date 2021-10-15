setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxRGLMResultFrame <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  maxrglm_model <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel)
  bestR2Value <- h2o.get_best_r2_values(maxrglm_model)
  resultFrame <- h2o.result(maxrglm_model)

  for (ind in c(1:numModel)) {
    r2 <- bestR2Value[ind]
    r2Frame <- resultFrame[ind, 3] # get r2
    glmModel <- h2o.getModel(resultFrame[ind, 2])
    predFrame <- h2o.predict(glmModel, bhexFV)
    print(predFrame[1,1])
    r2Model <- h2o.r2(glmModel)
    expect_equal(r2, r2Frame, tolerance=1e-6)
    expect_equal(r2Frame, r2Model, tolerance=1e-06)
  }
}

doTest("MaxRGLM: test result frame", testMaxRGLMResultFrame)
