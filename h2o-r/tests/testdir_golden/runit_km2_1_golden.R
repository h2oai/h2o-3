setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.km2vanilla.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing iris.csv data...") 
  irisH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/iris/iris2.csv"), key = "irisH2O")
  irisR <- read.csv(locate("smalldata/iris/iris2.csv"), header = TRUE)
  startIdx <- sample(1:nrow(irisR), 3)
  
  # H2O automatically standardizes data (de-mean and scale so standard deviation is one)
  irisScale = scale(irisR[,1:4], center = TRUE, scale = TRUE)
  fitR <- kmeans(irisScale, centers = irisScale[startIdx,1:4], iter.max = 1000, nstart = 10)
  fitH2O <- h2o.kmeans(irisH2O[,1:4], k = 3, init = irisH2O[startIdx,1:4])
  
  wmseR <- sort.int(fitR$withinss/fitR$size)
  wmseH2O <- sort.int(fitH2O@model$withinmse)
  totssR <- fitR$totss
  totssH2O <- fitH2O@model$avgss*nrow(iris)
  btwssR <- fitR$betweenss
  btwssH2O <- fitH2O@model$avgbetweenss*nrow(iris)
  
  Log.info(paste("H2O WithinMSE : ", wmseH2O, "\t\t", "R WithinMSE : ", wmseR))
  Log.info("Compare Within-Cluster MSE between R and H2O\n")  
  expect_equal(wmseR, wmseH2O, tolerance = 0.10)
  
  Log.info(paste("H2O TotalSS : ", totssH2O, "\t\t", "R TotalSS : ", totssR))
  Log.info("Compare Total SS between R and H2O\n")
  expect_equal(totssR, totssH2O)
  
  Log.info(paste("H2O BtwSS : ", btwssH2O, "\t\t", "R BtwSS : ", btwssR))
  Log.info("Compare Between-Cluster SS between R and H2O\n")
  expect_equal(btwssR, btwssH2O, tolerance = 0.10)
  
  testEnd()
}

doTest("KMeans Test: Golden Kmeans2 - Vanilla Iris Clustering", test.km2vanilla.golden)