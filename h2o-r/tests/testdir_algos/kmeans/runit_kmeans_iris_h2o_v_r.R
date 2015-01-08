setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# Test k-means clustering on benign.csv
test.km.iris <- function(conn) {
  iris.hex <- as.h2o(conn, iris)
  
  start <- iris[c(2,70,148),1:4]

  iris.km.r <- kmeans(iris[,1:4], centers=start, algorithm="Lloyd")
  iris.km.h2o <- h2o.kmeans(iris.hex[,1:4], init=start, standardize=FALSE)

  print(iris.km.r$centers)
  print(iris.km.h2o@model$centers)

  for (r in 1:3) {
    for (c in 1:4) {
      expect_equal(as.numeric(iris.km.h2o@model$centers[r,c]), as.numeric(iris.km.r$centers[r,c]))
    }
  }

  testEnd()
}

doTest("KMeans Test: Iris Data (h2o vs. R", test.km.iris)
