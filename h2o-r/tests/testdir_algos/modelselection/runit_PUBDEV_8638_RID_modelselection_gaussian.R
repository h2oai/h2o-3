setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionRIDGaussian <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X   <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  
  Log.info("Build the MaxRGLM model")
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=3, 
                                  mode="maxr", influence="dfbetas", standardize=FALSE)
  maxrRID = h2o.get_regression_influence_diagnostics(maxrModel)
  maxrsweepModelGLM <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=3,
                                          mode="maxrsweep", build_glm_model=TRUE, influence="dfbetas", standardize=FALSE)
  maxrsweepRID = h2o.get_regression_influence_diagnostics(maxrsweepModelGLM)
  backwardModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, min_predictor_number=1, 
                                      mode="backward", influence="dfbetas", standardize=FALSE)
  backwardRID <- h2o.get_regression_influence_diagnostics(backwardModel)
  # compare the RID frame contents
  colNames <- c("DFBETA_CAPSULE", "DFBETA_Intercept")
  compareFrameCols(maxrRID[[1]], maxrsweepRID[[1]], colNames)
  compareFrameCols(maxrRID[[1]], backwardRID[[1]], colNames)
  colNames <- c("DFBETA_CAPSULE", "DFBETA_PSA", "DFBETA_Intercept")
  compareFrameCols(maxrRID[[2]], maxrsweepRID[[2]], colNames)
  compareFrameCols(maxrRID[[2]], backwardRID[[2]], colNames)
  colNames <- c("DFBETA_CAPSULE", "DFBETA_PSA", "DFBETA_DCAPS", "DFBETA_Intercept")
  compareFrameCols(maxrRID[[3]], maxrsweepRID[[3]], colNames)
  compareFrameCols(maxrRID[[3]], backwardRID[[3]], colNames)
}

compareFrameCols <- function(frame1, frame2, colNames) {
  for (colName in colNames) {
    compareFrames(frame1[,colName], frame2[,colName], prob=1)
  }
}

doTest("ModelSelection with backward, maxr, maxrsweep: Gaussian data and checking the regression influence diagnostics", testModelSelectionRIDGaussian)
