setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.km.init_err <- function() {
  h2oTest.logInfo("Importing benign.csv data...\n")
  benign.hex <- h2o.uploadFile( h2oTest.locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)
  numcol <- ncol(benign.hex)
  numrow <- nrow(benign.hex)
  
  h2oTest.logInfo("Non-numeric entry that isn't 'Random', 'PlusPlus', or 'Furthest'")
  expect_error(h2o.kmeans(benign.hex, init = NA))
  expect_error(h2o.kmeans(benign.hex, init = "Test123"))
  
  h2oTest.logInfo("Empty matrix, list, or data frame")
  expect_error(h2o.kmeans(benign.hex, init = list()))
  expect_error(h2o.kmeans(benign.hex, init = matrix(list())))
  expect_error(h2o.kmeans(benign.hex, init = data.frame()))
  
  h2oTest.logInfo("Number of columns doesn't equal training set's")
  start_small <- matrix(rnorm(5*(numcol - 2)), 5, numcol - 2)
  start_large <- matrix(rnorm(5*(numcol + 2)), 5, numcol + 2)
  expect_error(h2o.kmeans(benign.hex, init = start_small))
  expect_error(h2o.kmeans(benign.hex, init = start_large))
  
  h2oTest.logInfo("Number of rows exceeds training set's")
  start <- matrix(rnorm((numrow + 2)*numcol), numrow + 2, numcol)
  expect_error(h2o.kmeans(benign.hex, init = start))
  
  # NAs are replaced with mean of a column in H2O. Not sure about Inf.
  h2oTest.logInfo("Any entry is NA, NaN, or Inf")
  start <- matrix(rnorm(3*numcol), 3, numcol)
  for(x in c(NA, NaN, Inf, -Inf)) {
    start_err <- start
    start_err[2,sample(1:numcol, 1)] <- x
    h2o.kmeans(benign.hex, init = start_err)
  }
  
  # Duplicates will affect sampling probability during initialization.
  h2oTest.logInfo("Duplicate initial clusters specified")
  start <- matrix(rnorm(3*numcol), 3, numcol)
  start[3,] <- start[1,]    # Row 3 is duplicate of row 1
  h2o.kmeans(benign.hex, init = start)
  
  
}

h2oTest.doTest("KMeans Test: User-specified initial cluster error cases", test.km.init_err)
