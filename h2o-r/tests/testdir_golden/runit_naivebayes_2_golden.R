setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.nbayes.golden <- function() {
  h2oTest.logInfo("Importing iris_wheader.csv data...") 
  irisR <- read.csv(h2oTest.locate("smalldata/iris/iris_wheader.csv"), header = TRUE)
  irisH2O <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"), destination_frame = "irisH2O")
  
  smoothing <- rnorm(1, mean = 1e-6, sd = 5)^2
  h2oTest.logInfo(paste("Compare with Naive Bayes when x = 1:4, y = 5, laplace =", smoothing))
  fitR <- naiveBayes(class ~ ., irisR, laplace = smoothing)
  fitH2O <- h2o.naiveBayes(x = 1:4, y = 5, training_frame = irisH2O, laplace = smoothing)
  h2oTest.checkNaiveBayesModel(fitH2O, fitR, nrow(irisR), tolerance = 1e-4)
  
  h2oTest.logInfo("Compare Predictions between Models")
  classR <- predict(fitR, irisR, type = "class")
  postR <- predict(fitR, irisR, type = "raw")
  predH2O <- predict(fitH2O, irisH2O)
  h2oTest.checkNaiveBayesPrediction(predH2O, classR, type = "class", tolerance = 1e-4)
  h2oTest.checkNaiveBayesPrediction(predH2O, postR, type = "raw", tolerance = 1e-4)
  
  
}

h2oTest.doTest("Naive Bayes Golden Test: Iris with Laplace Smoothing", test.nbayes.golden)
