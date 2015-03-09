setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.nbayes.golden <- function(H2Oserver) {
  Log.info("Importing iris_wheader.csv data...") 
  irisR <- read.csv(locate("smalldata/iris/iris_wheader.csv"), header = TRUE)
  irisH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/iris/iris_wheader.csv"), key = "irisH2O")
  
  Log.info("Compare with Naive Bayes when x = 1:4, y = 5, laplace = 0")
  fitR <- NaiveBayes(class ~ ., irisR, usekernel = 0, fL = 0)
  fitH2O <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = irisH2O, laplace = 0)
  checkNaiveBayesModel(fitH2O, fitR, tolerance = 1e-6)
  
  Log.info("Compare Predictions between Models")
  predR <- predict(fitR, irisR)
  predH2O <- predict(fitH2O, irisH2O)
  checkNaiveBayesPrediction(predH2O, predR, tolerance = 1e-6)
  
  testEnd()
}

doTest("Naive Bayes Golden Test: Iris without Laplace smoothing", test.nbayes.golden)