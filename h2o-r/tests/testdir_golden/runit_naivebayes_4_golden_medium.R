setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.nbayes.golden <- function() {
  Log.info("Importing covtype.20k.data training data...") 
  covtypeR <- read.csv(locate("smalldata/covtype/covtype.20k.data"), header = FALSE)
  covtypeH2O <- h2o.uploadFile(locate("smalldata/covtype/covtype.20k.data"), destination_frame = "covtypeH2O", header = FALSE)
  expect_equal(dim(covtypeH2O), dim(covtypeR))
  
  Log.info("Converting response y = 55 to a factor...")
  covtypeR[,55] <- as.factor(covtypeR[,55])
  covtypeH2O[,55] <- as.factor(covtypeH2O[,55])
  
  Log.info("Dropping constant columns V21 and V29 from R data...")
  colnames(covtypeH2O) <- colnames(covtypeR)
  covtypeR <- subset(covtypeR, select = -c(V21, V29))
  
  Log.info("Compare with Naive Bayes when x = 1:54, y = 55, laplace = 0")
  fitR <- naiveBayes(V55 ~ ., covtypeR, laplace = 0)
  fitH2O <- h2o.naiveBayes(x = 1:54, y = 55, training_frame = covtypeH2O, laplace = 0, threshold = 0.001, eps = 0)
  checkNaiveBayesModel(fitH2O, fitR, nrow(covtypeR), tolerance = 1e-4)
  
  Log.info("Compare Predictions between Models")
  classR <- predict(fitR, covtypeR, type = "class", threshold = 0.001, eps = 0)
  postR <- predict(fitR, covtypeR, type = "raw", threshold = 0.001, eps = 0)
  predH2O <- predict(fitH2O, covtypeH2O)
  checkNaiveBayesPrediction(predH2O, classR, type = "class", tolerance = 1e-4)
  checkNaiveBayesPrediction(predH2O, postR, type = "raw", tolerance = 1e-4)
  
  testEnd()
}

doTest("Naive Bayes Golden Test: Covtype without Laplace smoothing", test.nbayes.golden)
