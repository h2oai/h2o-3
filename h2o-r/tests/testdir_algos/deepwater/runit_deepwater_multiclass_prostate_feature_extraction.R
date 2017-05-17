setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.deeplearning_multi <- function() {
  if (!h2o.deepwater.available()) return()

  Log.info("Test checks if Deep Water works fine with a multiclass training and test dataset")
  
  prostate <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  prostate[,"GLEASON"] <- as.factor(prostate[,"GLEASON"])

  hh <- h2o.deepwater(x=c("CAPSULE","AGE","RACE","DPROS","DCAPS","PSA","VOL"),y="GLEASON",training_frame=prostate[1:300,],validation_frame=prostate[301:380,], epochs=100)
  print(hh)

  df <- h2o.deepfeatures(hh, prostate[1:10,],"fc2_output")
  print(df)
}

doTest("Deep Water MultiClass Test", check.deeplearning_multi)

