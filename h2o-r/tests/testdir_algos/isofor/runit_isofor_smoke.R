setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.IsolationForest.smoke <- function() {
    set.seed(1234)
    N = 1e4
    random_data <- data.frame(
        x = c(rnorm(N, 0, 0.5), rnorm(N*0.05, -1.5, 1)),
        y = c(rnorm(N, 0, 0.5), rnorm(N*0.05,  1.5, 1))
    )
    random_data.hex <- as.h2o(random_data)

    isofor.model <- h2o.isolationForest(training_frame = random_data.hex)
    expect_equal(as.character(class(isofor.model)), "H2OAnomalyDetectionModel")
}

doTest("IsolationForest: Smoke Test", test.IsolationForest.smoke)