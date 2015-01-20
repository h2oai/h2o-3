setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.kmvanilla.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing ozone.csv data...") 
  ozoneR <- read.csv(locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/glm_test/ozone.csv"), key = "ozoneH2O")
  startIdx <- sort(sample(1:nrow(ozoneR), 3))
  
  Log.info("Initial cluster centers:"); print(ozoneR[startIdx,])
  fitR <- kmeans(ozoneR, centers = ozoneR[startIdx,], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(ozoneH2O, init = ozoneH2O[startIdx,], standardize = FALSE)
  
  Log.info("R Final Clusters:"); print(fitR$centers)
  Log.info("H2O Final Clusters:"); print(fitH2O@model$centers)
  expect_equivalent(as.matrix(fitH2O@model$centers), fitR$centers)
  
  wmseR <- sort.int(fitR$withinss/fitR$size)
  wmseH2O <- sort.int(fitH2O@model$within_mse)
  totssR <- fitR$totss
  totssH2O <- fitH2O@model$avg_ss*nrow(ozoneH2O)
  btwssR <- fitR$betweenss
  btwssH2O <- fitH2O@model$avg_between_ss*nrow(ozoneH2O)
  
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
  classH2O <- predict(fitH2O, ozoneH2O)
  expect_equivalent(as.numeric(as.matrix(classH2O))+1, classR)   # H2O indexes from 0, but R indexes from 1
  
  testEnd()
}

doTest("KMeans Test: Golden Kmeans - Ozone without Standardization", test.kmvanilla.golden)