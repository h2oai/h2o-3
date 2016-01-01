setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



# Compare within-cluster sum of squared error
test.kmslice.golden <- function() {
  # Import data: 
  h2oTest.logInfo("Importing iris.csv data...") 
  irisR <- read.csv(h2oTest.locate("smalldata/iris/iris2.csv"), header = TRUE)
  irisH2O <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris2.csv"), destination_frame = "irisH2O")
  # iris has some duplicated rows. Want to guarantee unique init centers
  # Still failing intermittently..a random init from the data, is the same as kmeans rand init ..
  # So that doesn't guarantee one true answer.
  # startIdx <- sort(sample(unique(1:nrow(irisR)), 3))
  
  # full iris is used for test and train, so these indices always point to the same data
  startIdx <- c(1, 52, 102) # use the first row from each output class
  
  h2oTest.logInfo("Initial cluster centers:"); print(irisR[startIdx,1:4])
  fitR <- kmeans(irisR[,1:4], centers = irisR[startIdx,1:4], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(irisH2O[,1:4], init = irisH2O[startIdx,1:4], standardize = FALSE)
  h2oTest.checkKMeansModel(fitH2O, fitR, tol = 0.01)
  
  h2oTest.logInfo("Compare Predicted Classes between R and H2O\n")
  classR <- fitted(fitR, method = "classes")
  # FIXME: predict directly on sliced H2O frame breaks
  # classH2O <- predict(fitH2O, irisH2O[,1:4])

  classH2O <- predict(fitH2O, as.h2o(irisR[,1:4]))
  # H2O indexes from 0, but R indexes from 1
  forCompareH2O <- as.matrix(classH2O)+1
  forCompareR <- as.matrix(classR)
  notMatchingH2O <- forCompareH2O[forCompareH2O != forCompareR]
  notMatchingR <- forCompareR[forCompareH2O != forCompareR]

  h2oTest.logInfo(dim(forCompareH2O))
  h2oTest.logInfo(dim(forCompareR))
  h2oTest.logInfo(head(forCompareH2O))
  h2oTest.logInfo(head(forCompareR))
  h2oTest.logInfo(dim(notMatchingH2O))
  h2oTest.logInfo(head(notMatchingH2O))
  h2oTest.logInfo(head(notMatchingR))

  h2oTest.logInfo(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))

  # one has dim names, the other doesn't. will get length error unless..
  expect_true(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))
  
  
}

h2oTest.doTest("KMeans Test: Golden Kmeans - Iris without Standardization", test.kmslice.golden)
