setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.nbayes.prostate <- function() {
  h2oTest.logInfo("Importing prostate.csv data...") 
  prostate.hex <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame= "prostate.hex")
  
  h2oTest.logInfo("Converting CAPSULE, RACE, DCAPS, and DPROS to categorical")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.hex$RACE <- as.factor(prostate.hex$RACE)
  prostate.hex$DCAPS <- as.factor(prostate.hex$DCAPS)
  prostate.hex$DPROS <- as.factor(prostate.hex$DPROS)
  
  h2oTest.logInfo("Compare with Naive Bayes when x = 3:9, y = 2")
  prostate.nb <- h2o.naiveBayes(x = 3:9, y = 2, training_frame = prostate.hex, laplace = 0)
  print(prostate.nb)
  
  h2oTest.logInfo("Predict on training data")
  prostate.pred <- predict(prostate.nb, prostate.hex)
  print(head(prostate.pred))
  
}

h2oTest.doTest("Naive Bayes Test: Prostate without Laplace Smoothing", test.nbayes.prostate)
