setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.kmstand.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing ozone.csv data...")
  ozoneR <- read.csv(locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/glm_test/ozone.csv"), key = "ozoneH2O")
  startIdx <- sort(sample(1:nrow(ozoneR), 3))
  
  # H2O standardizes data (de-mean and scale so standard deviation is one)
  ozoneScale = scale(ozoneR, center = TRUE, scale = TRUE)
  Log.info("Initial cluster centers:"); print(ozoneScale[startIdx,])
  fitR <- kmeans(ozoneScale, centers = ozoneScale[startIdx,], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(ozoneH2O, init = ozoneH2O[startIdx,], standardize = TRUE)
  
  Log.info("R Final Clusters:"); print(fitR$centers)
  Log.info("H2O Final Clusters (de-standardized):"); print(fitH2O@model$centers)
  
  # De-standardize R final clusters for comparison with H2O
  avg <- apply(ozoneR, 2, mean)
  std <- apply(ozoneR, 2, sd)
  fitR_centstd <- sweep(sweep(fitR$centers, 2, std, '*'), 2, avg, "+")
  expect_equivalent(as.matrix(fitH2O@model$centers), fitR_centstd)
  
  wmseR <- sort.int(fitR$withinss/fitR$size)
  wmseH2O <- sort.int(fitH2O@model$withinmse)
  totssR <- fitR$totss
  totssH2O <- fitH2O@model$avgss*nrow(ozoneH2O)
  btwssR <- fitR$betweenss
  btwssH2O <- fitH2O@model$avgbetweenss*nrow(ozoneH2O)
  
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
  # Need to compute distance of original data from de-standardized centers
  # R's fitted method computes distance of standardized data from standardized centers
  # classR <- fitted(fitR, method = "classes")
  clusters <- function(x, centers) {
    tmp <- sapply(seq_len(nrow(x)), function(i) apply(centers, 1, function(v) sum((x[i, ]-v)^2)))
    max.col(-t(tmp))  # find index of min distance
  }
  classR <- clusters(ozoneR, fitR_centstd)
  classH2O <- as.matrix(predict(fitH2O, ozoneH2O))
  expect_equivalent(as.numeric(as.matrix(classH2O))+1, classR)   # H2O indexes from 0, but R indexes from 1
  
  testEnd()
}

doTest("KMeans Test: Golden Kmeans - Ozone with Standardization", test.kmstand.golden)