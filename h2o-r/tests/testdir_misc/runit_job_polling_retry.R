setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

jobRequestCounter <- 0
origSafeGet <- .h2o.doSafeGET

.h2o.doSafeGET <- function(h2oRestApiVersion, urlSuffix, parms, ...) {
  if (jobRequestCounter == 2) {
    jobRequestCounter <<- jobRequestCounter + 1
    stop("Simulated connection error")
  } else {
    jobRequestCounter <<- jobRequestCounter + 1
    origSafeGet(h2oRestApiVersion, urlSuffix, parms, ...)
  }
}

test.jobPoolRetry <- function () {
  set.seed(1234)
  N = 1e4
  random_data <- data.frame(
    x = c(rnorm(N, 0, 0.5), rnorm(N*0.05, -1.5, 1)),
    y = c(rnorm(N, 0, 0.5), rnorm(N*0.05,  1.5, 1))
  )
  random_data.hex <- as.h2o(random_data)

  h2o.isolationForest(ntrees = 100, training_frame = random_data.hex)
}

doTest("Test Job poll retry", test.jobPoolRetry)
