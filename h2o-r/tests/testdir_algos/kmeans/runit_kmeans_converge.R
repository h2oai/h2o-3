setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.km.iter_max <- function() {
  h2oTest.logInfo("Importing ozone.csv data...\n")
  ozone.hex <- h2o.uploadFile( h2oTest.locate("smalldata/glm_test/ozone.csv"))
  print(summary(ozone.hex))
  miters <- 5
  ncent <- 10
  
  h2oTest.logInfo(paste("Run k-means in a loop of", miters, "iterations with max_iter = 1"))
  start <- ozone.hex[1:ncent,]
  expect_error(h2o.kmeans(ozone.hex, max_iterations = 0))
  for(i in 1:miters) {
    fitrep <- h2o.kmeans(ozone.hex, init = start, max_iterations = 1)
    start <- getCenters(fitrep)
  }
  h2oTest.logInfo(paste("Run k-means with max_iter =", miters))
  fitall <- h2o.kmeans(ozone.hex, init = ozone.hex[1:ncent,], max_iterations = miters)
  expect_equivalent(getCenters(fitrep), getCenters(fitall))
  
  h2oTest.logInfo("Check cluster centers have converged")
  fitall2 <- h2o.kmeans(ozone.hex, init = getCenters(fitall), max_iter = 1)
  avg_change <- sum((getCenters(fitall) - getCenters(fitall2))^2)/ncent
  expect_true(avg_change < 1e-6 || getIterations(fitall) == miters)  # Either converged or hit max_iterations limit
  
}

h2oTest.doTest("KMeans Test: Test convergence at max iterations", test.km.iter_max)
