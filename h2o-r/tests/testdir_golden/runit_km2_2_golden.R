setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.kmstand.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing iris.csv data...") 
  irisH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/iris/iris2.csv"), key = "irisH2O")
  irisR <- read.csv(locate("smalldata/iris/iris2.csv"), header = TRUE)
  startIdx <- sort(sample(1:nrow(irisR), 3))
  
  # H2O standardizes data (de-mean and scale so standard deviation is one)
  irisScale = scale(irisR[,1:4], center = TRUE, scale = TRUE)
  Log.info("Initial cluster centers:"); print(irisScale[startIdx,])
  fitR <- kmeans(irisScale, centers = irisScale[startIdx,], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(irisH2O[,1:4], init = irisH2O[startIdx,1:4], standardize = TRUE)
  
  Log.info("R Final Clusters:"); print(fitR$centers)
  Log.info("H2O Final Clusters (de-standardized):"); print(fitH2O@model$centers)
  
  # De-standardize R final clusters for comparison with H2O
  avg <- apply(irisR[,1:4], 2, mean)
  std <- apply(irisR[,1:4], 2, sd)
  fitR_centstd <- sweep(sweep(fitR$centers, 2, std, '*'), 2, avg, "+")
  expect_equivalent(as.matrix(fitH2O@model$centers), fitR_centstd)
  
  wmseR <- sort.int(fitR$withinss/fitR$size)
  wmseH2O <- sort.int(fitH2O@model$withinmse)
  totssR <- fitR$totss
  totssH2O <- fitH2O@model$avgss*nrow(irisH2O)
  btwssR <- fitR$betweenss
  btwssH2O <- fitH2O@model$avgbetweenss*nrow(irisH2O)
  
  Log.info(paste("H2O WithinMSE : ", wmseH2O, "\t\t", "R WithinMSE : ", wmseR))
  Log.info("Compare Within-Cluster MSE between R and H2O\n")  
  expect_equal(wmseH2O, wmseR, tolerance = 0.01)
  
  Log.info(paste("H2O TotalSS : ", totssH2O, "\t\t", "R TotalSS : ", totssR))
  Log.info("Compare Total SS between R and H2O\n")
  expect_equal(totssH2O, totssR)
  
  Log.info(paste("H2O BtwSS : ", btwssH2O, "\t\t", "R BtwSS : ", btwssR))
  Log.info("Compare Between-Cluster SS between R and H2O\n")
  expect_equal(btwssH2O, btwssR, tolerance = 0.01)
  
  testEnd()
}

doTest("KMeans Test: Golden Kmeans - Iris with Standardization", test.kmstand.golden)