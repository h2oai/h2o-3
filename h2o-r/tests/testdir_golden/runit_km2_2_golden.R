setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



# Compare within-cluster sum of squared error
test.kmstand.golden <- function() {
  # Import data: 
  h2oTest.logInfo("Importing ozone.csv data...")
  ozoneR <- read.csv(h2oTest.locate("smalldata/glm_test/ozone.csv"), header = TRUE)
  ozoneH2O <- h2o.uploadFile(h2oTest.locate("smalldata/glm_test/ozone.csv"), destination_frame = "ozoneH2O")
  startIdx <- sort(sample(1:nrow(ozoneR), 3))
  
  # H2O standardizes data (de-mean and scale so standard deviation is one)
  ozoneScale = scale(ozoneR, center = TRUE, scale = TRUE)
  h2oTest.logInfo("Initial cluster centers:"); print(ozoneScale[startIdx,])
  fitR <- kmeans(ozoneScale, centers = ozoneScale[startIdx,], iter.max = 1000, algorithm = "Lloyd")
  fitH2O <- h2o.kmeans(ozoneH2O, init = ozoneH2O[startIdx,], standardize = TRUE)
  
  h2oTest.logInfo("R Final Clusters:"); print(fitR$centers)
  h2oTest.logInfo("H2O Final Clusters:"); print(getCentersStd(fitH2O))
  h2oTest.logInfo("H2O Final Clusters (de-standardized):"); print(getCenters(fitH2O))
  expect_equivalent(as.matrix(getCentersStd(fitH2O)), fitR$centers)
  
  # De-standardize R final clusters for comparison with H2O
  avg <- apply(ozoneR, 2, mean)
  std <- apply(ozoneR, 2, sd)
  fitR_centstd <- sweep(sweep(fitR$centers, 2, std, '*'), 2, avg, "+")
  expect_equivalent(as.matrix(getCenters(fitH2O)), fitR_centstd)
  
  wssR <- sort.int(fitR$withinss)
  wssH2O <- sort.int(getWithinSS(fitH2O))
  totssR <- fitR$totss
  totssH2O <- getTotSS(fitH2O)
  btwssR <- fitR$betweenss
  btwssH2O <- getBetweenSS(fitH2O)
  
  h2oTest.logInfo(paste("H2O WithinSS : ", wssH2O, "\t\t", "R WithinSS : ", wssR))
  h2oTest.logInfo("Compare Within-Cluster SS between R and H2O\n")  
  expect_equal(wssH2O, wssR, tolerance = 0.01)
  
  h2oTest.logInfo(paste("H2O TotalSS : ", totssH2O, "\t\t", "R TotalSS : ", totssR))
  h2oTest.logInfo("Compare Total SS between R and H2O\n")
  expect_equal(totssH2O, totssR)
  
  h2oTest.logInfo(paste("H2O BtwSS : ", btwssH2O, "\t\t", "R BtwSS : ", btwssR))
  h2oTest.logInfo("Compare Between-Cluster SS between R and H2O\n")
  expect_equal(btwssH2O, btwssR, tolerance = 0.01)
  
  h2oTest.logInfo("Compare Predicted Classes between R and H2O\n")
  classR <- fitted(fitR, method = "classes")
  classH2O <- as.matrix(predict(fitH2O, ozoneH2O))
  expect_equivalent(as.numeric(as.matrix(classH2O))+1, classR)   # H2O indexes from 0, but R indexes from 1
  
  
}

h2oTest.doTest("KMeans Test: Golden Kmeans - Ozone with Standardization", test.kmstand.golden)
