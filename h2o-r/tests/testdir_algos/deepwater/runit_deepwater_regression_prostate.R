setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.deeplearning_regression <- function() {
  if (!h2o.deepwater.available()) return()

  Log.info("Test checks if Deep Water works fine with a regression training and test dataset")
  
  prostate <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))

  hh <- h2o.deepwater(x=c("CAPSULE","GLEASON","RACE","DPROS","DCAPS","PSA","VOL"),y="AGE",training_frame=prostate[1:300,],validation_frame=prostate[301:380,], epochs=100)
  print(hh)
}

doTest("Deep Water MultiClass Test", check.deeplearning_regression)

