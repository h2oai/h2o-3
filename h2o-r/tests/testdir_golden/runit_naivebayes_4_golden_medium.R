setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.nbayes.golden <- function() {
  h2oTest.logInfo("Importing covtype.20k.data training data...") 
  covtypeR <- read.csv(h2oTest.locate("smalldata/covtype/covtype.20k.data"), header = FALSE)
  covtypeH2O <- h2o.uploadFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"), destination_frame = "covtypeH2O", header = FALSE)
  expect_equal(dim(covtypeH2O), dim(covtypeR))
  
  h2oTest.logInfo("Converting response y = 55 to a factor...")
  covtypeR[,55] <- as.factor(covtypeR[,55])
  covtypeH2O[,55] <- as.factor(covtypeH2O[,55])
  
  h2oTest.logInfo("Dropping constant columns V21 and V29 from R data...")
  colnames(covtypeH2O) <- colnames(covtypeR)
  covtypeR <- subset(covtypeR, select = -c(V21, V29))
  
  h2oTest.logInfo("Compare with Naive Bayes when x = 1:54, y = 55, laplace = 0")
  fitR <- naiveBayes(V55 ~ ., covtypeR, laplace = 0)
  fitH2O <- h2o.naiveBayes(x = 1:54, y = 55, training_frame = covtypeH2O, laplace = 0, threshold = 0.001, eps = 0)
  h2oTest.checkNaiveBayesModel(fitH2O, fitR, nrow(covtypeR), tolerance = 1e-4)
  
  h2oTest.logInfo("Compare Predictions between Models")
  classR <- predict(fitR, covtypeR, type = "class", threshold = 0.001, eps = 0)
  postR <- predict(fitR, covtypeR, type = "raw", threshold = 0.001, eps = 0)
  predH2O <- predict(fitH2O, covtypeH2O)
  h2oTest.checkNaiveBayesPrediction(predH2O, classR, type = "class", tolerance = 1e-4)
  h2oTest.checkNaiveBayesPrediction(predH2O, postR, type = "raw", tolerance = 1e-4)
  
  
}

h2oTest.doTest("Naive Bayes Golden Test: Covtype without Laplace smoothing", test.nbayes.golden)
