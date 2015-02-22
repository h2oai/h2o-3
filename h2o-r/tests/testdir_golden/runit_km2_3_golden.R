setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.kmslice.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing iris.csv data...") 
  irisR <- read.csv(locate("smalldata/iris/iris2.csv"), header = TRUE)
  irisH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/iris/iris2.csv"), key = "irisH2O")
  # iris has some duplicated rows. Want to guarantee unique init centers
  # Still failing intermittently..a random init from the data, is the same as kmeans rand init ..
  # So that doesn't guarantee one true answer.
  # startIdx <- sort(sample(unique(1:nrow(irisR)), 3))
  
  startIdx <- c(1, 52, 102) # use the first row from each output class
  
  Log.info("Initial cluster centers:"); print(irisR[startIdx,1:4])
  fitR <- kmeans(irisR[,1:4], centers = irisR[startIdx,1:4], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(irisH2O[,1:4], init = irisH2O[startIdx,1:4], standardize = FALSE)
  
  Log.info("R Final Clusters:"); print(fitR$centers)
  Log.info("H2O Final Clusters:"); print(fitH2O@model$centers)
  expect_equal(as.matrix(fitH2O@model$centers), fitR$centers, tolerance = 0.01)
  
  wmseR <- sort.int(fitR$withinss/fitR$size)
  wmseH2O <- sort.int(fitH2O@model$within_mse)
  totssR <- fitR$totss
  totssH2O <- fitH2O@model$avg_ss*nrow(irisH2O)
  btwssR <- fitR$betweenss
  btwssH2O <- fitH2O@model$avg_between_ss*nrow(irisH2O)
  
  Log.info(paste("H2O WithinMSE : ", wmseH2O, "\t\t", "R WithinMSE : ", wmseR))
  Log.info("Compare Within-Cluster MSE between R and H2O\n")  
  expect_equal(wmseH2O, wmseR, tolerance = 0.01)
  
  Log.info(paste("H2O TotalSS : ", totssH2O, "\t\t", "R TotalSS : ", totssR))
  Log.info("Compare Total SS between R and H2O\n")
  expect_equal(totssH2O, totssR)
  
  Log.info(paste("H2O BtwSS : ", btwssH2O, "\t\t", "R BtwSS : ", btwssR))
  Log.info("Compare Between-Cluster SS between R and H2O\n")
  expect_equal(btwssH2O, btwssR, tolerance = 0.01)
  
  Log.info("Compare Predicted Classes between R and H2O\n")
  classR <- fitted(fitR, method = "classes")
  # FIXME: predict directly on sliced H2O frame breaks
  # classH2O <- predict(fitH2O, irisH2O[,1:4])

  classH2O <- predict(fitH2O, as.h2o(conn, irisR[,1:4]))
  # H2O indexes from 0, but R indexes from 1
  forCompareH2O <- as.matrix(classH2O)+1
  forCompareR <- as.matrix(classR)
  notMatching <- forCompareH2O[forCompareH2O == forCompareR]

  Log.info(dim(forCompareH2O))
  Log.info(dim(forCompareR))
  Log.info(head(forCompareH2O))
  Log.info(head(forCompareR))
  Log.info(head(notMatching))

  Log.info(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))

  # one has dim names, the other doesn't. will get length error unless..
  expect_true(all.equal(forCompareH2O, forCompareR, check.attributes=FALSE))
  
  testEnd()
}

doTest("KMeans Test: Golden Kmeans - Iris without Standardization", test.kmslice.golden)
