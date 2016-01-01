setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test k-means clustering on R's iris dataset
test.km.iris <- function() {
  iris.hex <- as.h2o( iris)
  start <- iris[c(2,70,148),1:4]
  start.hex <- iris.hex[c(2,70,148),1:4]

  iris.km.r <- kmeans(iris[,1:4], centers=start, algorithm="Lloyd", iter.max = 1000)
  iris.km.h2o <- h2o.kmeans(iris.hex[,1:4], init=start, standardize=FALSE)
  iris.km.h2o2 <- h2o.kmeans(iris.hex[,1:4], init=start.hex, standardize=FALSE)

  h2oTest.logInfo("Cluster centers from R:")
  print(iris.km.r$centers)
  h2oTest.logInfo("Cluster centers from H2O:")
  print(getCenters(iris.km.h2o))

  centersH2O <- getCenters(iris.km.h2o)
  centersH2O2 <- getCenters(iris.km.h2o2)
  for (r in 1:3) {
    for (c in 1:4) {
      # Sliced initial centers should match R matrix passed across REST API. Both should match R's output.
      expect_equal(as.numeric(centersH2O[r,c]), as.numeric(centersH2O2[r,c]))
      expect_equal(as.numeric(centersH2O[r,c]), as.numeric(iris.km.r$centers[r,c]))
    }
  }

  
}

h2oTest.doTest("KMeans Test: Iris Data (H2O vs. R)", test.km.iris)
