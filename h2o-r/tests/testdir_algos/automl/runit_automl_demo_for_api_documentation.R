setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.automl.documentation.suite <- function() {

  r_demo <- function() {
    prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
    prostate_hf <- h2o.uploadFile(path = prostate_path, header = TRUE)
    y <- "CAPSULE"
    prostate_hf[,y] <- as.factor(prostate_hf[,y])
    aml <- h2o.automl(y = y, training_frame = prostate_hf, max_runtime_secs = 30)

    lb <- h2o.get_leaderboard(aml)
    head(lb)
  }


  makeSuite(
    r_demo
  )
}

doSuite("Test for AutoML's R code examples", test.automl.documentation.suite(), time_monitor=TRUE)
