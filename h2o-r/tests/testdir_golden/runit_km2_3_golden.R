setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.kmslice.golden <- function() {
  # Import data: 
  Log.info("Importing iris.csv data...") 
  irisR <- read.csv(locate("smalldata/iris/iris2.csv"), header = TRUE)
  irisH2O <- h2o.uploadFile(locate("smalldata/iris/iris2.csv"), destination_frame = "irisH2O")
  # iris has some duplicated rows. Want to guarantee unique init centers
  # Still failing intermittently..a random init from the data, is the same as kmeans rand init ..
  # So that doesn't guarantee one true answer.
  # startIdx <- sort(sample(unique(1:nrow(irisR)), 3))
  
  # full iris is used for test and train, so these indices always point to the same data
  startIdx <- c(1, 52, 102) # use the first row from each output class
  
  Log.info("Initial cluster centers:"); print(irisR[startIdx,1:4])
  fitR <- kmeans(irisR[,1:4], centers = irisR[startIdx,1:4], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(irisH2O[,1:4], init = irisH2O[startIdx,1:4], standardize = FALSE)
  checkKMeansModel(fitH2O, fitR, tol = 0.01)
  
  Log.info("Compare Predicted Classes between R and H2O\n")
  classR <- fitted(fitR, method = "classes")
  # FIXME: predict directly on sliced H2O frame breaks
  # classH2O <- predict(fitH2O, irisH2O[,1:4])

  classH2O <- predict(fitH2O, as.h2o(irisR[,1:4]))
  # H2O indexes from 0, but R indexes from 1
  forCompareH2O <- as.matrix(classH2O)+1
  forCompareR <- as.matrix(classR)
  notMatchingH2O <- forCompareH2O[forCompareH2O != forCompareR]
  notMatchingR <- forCompareR[forCompareH2O != forCompareR]

  Log.info(dim(forCompareH2O))
  Log.info(dim(forCompareR))
  Log.info(head(forCompareH2O))
  Log.info(head(forCompareR))
  Log.info(dim(notMatchingH2O))
  Log.info(head(notMatchingH2O))
  Log.info(head(notMatchingR))

  Log.info(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))

  # one has dim names, the other doesn't. will get length error unless..
  expect_true(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))
  
  
}

doTest("KMeans Test: Golden Kmeans - Iris without Standardization", test.kmslice.golden)
