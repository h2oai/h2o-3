setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.nbayes.prostate <- function() {
  Log.info("Importing prostate.csv data...") 
  prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame= "prostate.hex")
  
  Log.info("Converting CAPSULE, RACE, DCAPS, and DPROS to categorical")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.hex$RACE <- as.factor(prostate.hex$RACE)
  prostate.hex$DCAPS <- as.factor(prostate.hex$DCAPS)
  prostate.hex$DPROS <- as.factor(prostate.hex$DPROS)
  
  Log.info("Compare with Naive Bayes when x = 3:9, y = 2")
  prostate.nb <- h2o.naiveBayes(x = 3:9, y = 2, training_frame = prostate.hex, laplace = 0)
  print(prostate.nb)
  
  Log.info("Predict on training data")
  prostate.pred <- predict(prostate.nb, prostate.hex)
  print(head(prostate.pred))
  
}

doTest("Naive Bayes Test: Prostate without Laplace Smoothing", test.nbayes.prostate)
