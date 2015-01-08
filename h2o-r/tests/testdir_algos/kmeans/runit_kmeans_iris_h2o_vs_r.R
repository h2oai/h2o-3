setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# Test k-means clustering on R's iris dataset
test.km.iris <- function(conn) {
  iris.hex <- as.h2o(conn, iris)
  start <- iris[c(2,70,148),1:4]
  start.hex <- iris.hex[c(2,70,148),1:4]

  iris.km.r <- kmeans(iris[,1:4], centers=start, algorithm="Lloyd")
  iris.km.h2o <- h2o.kmeans(iris.hex[,1:4], init=start, standardize=FALSE)
  iris.km.h2o2 <- h2o.kmeans(iris.hex[,1:4], init=start.hex, standardize=FALSE)

  Log.info("Cluster centers from R:")
  print(iris.km.r$centers)
  Log.info("Cluster centers from H2O:")
  print(iris.km.h2o@model$centers)

  for (r in 1:3) {
    for (c in 1:4) {
      # Sliced initial centers should match R matrix passed across REST API. Both should match R's output.
      expect_equal(as.numeric(iris.km.h2o@model$centers[r,c]), as.numeric(iris.km.h2o2@model$centers[r,c]))
      expect_equal(as.numeric(iris.km.h2o@model$centers[r,c]), as.numeric(iris.km.r$centers[r,c]))
    }
  }

  testEnd()
}

doTest("KMeans Test: Iris Data (H2O vs. R)", test.km.iris)
