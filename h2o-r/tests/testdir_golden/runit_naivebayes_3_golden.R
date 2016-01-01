setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.nbayes.golden <- function() {
  h2oTest.logInfo("Importing titanic_sub.csv data...") 
  titanicR <- read.csv(h2oTest.locate("smalldata/gbm_test/titanic_sub.csv"), header = TRUE)
  titanicH2O <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/titanic_sub.csv"), destination_frame = "titanicH2O")
  titanicR$survived <- as.factor(titanicR$survived)
  titanicH2O$survived <- as.factor(titanicH2O$survived)
  
  h2oTest.logInfo("Compare with Naive Bayes when x = 1:4, y = 5")
  # fitR <- NaiveBayes(survived ~ sex + age + sibsp + fare + embarked, titanicR, usekernel = 0, fL = 0)
  fitR <- naiveBayes(survived ~ sex + age + sibsp + fare + embarked, titanicR, laplace = 0)
  fitH2O <- h2o.naiveBayes(x = c(3, 4, 5, 7, 8), y = 2, training_frame = titanicH2O, laplace = 0)
  h2oTest.checkNaiveBayesModel(fitH2O, fitR, nrow(titanicH2O), tolerance = 1e-6)
  
  h2oTest.logInfo("Compare Predictions between Models")
  predR <- predict(fitR, titanicR)
  predH2O <- predict(fitH2O, titanicH2O)
  label <- ifelse(predH2O[,3] >= 0.5, 1, 0)
  #print(summary(predH2O))
  #print(summary(label))
  predH2O[,1] <- label
  #print(summary(predH2O))
  h2oTest.checkNaiveBayesPrediction(predH2O, predR, tolerance = 1e-6)
  
  
}

h2oTest.doTest("Naive Bayes Golden Test: Titanic without Laplace Smoothing", test.nbayes.golden)
