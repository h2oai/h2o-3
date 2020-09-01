setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.ExtendedIsolationForest.smoke <- function() {
    set.seed(1234)
    N = 1e4
    random_data <- data.frame(
        x = c(rnorm(N, 0, 0.5), rnorm(N*0.05, -1.5, 1)),
        y = c(rnorm(N, 0, 0.5), rnorm(N*0.05,  1.5, 1))
    )
    random_data.hex <- as.h2o(random_data)
    
    exisofor.model <- h2o.extendedIsolationForest(training_frame = random_data.hex)
    score <- h2o.predict(exisofor.model, random_data.hex)
    result_pred <- as.data.frame(score)$anomaly_score

    # The output of the EIF algorithm is based on randomly generated values. 
    # If the randomization is changed, then the output can be slightly different and it is fine to update them.
    # The link to source paper: https://arxiv.org/pdf/1811.02141.pdf
    result_expected <- c(0.4311024, 0.4023118, 0.4286952, 0.6100855, 0.4194065, 0.4420759)
    expect_equal(head(result_pred), result_expected, tolerance = 0.1, scale = 1)
    expect_equal(as.character(class(exisofor.model)), "H2OAnomalyDetectionModel")
}

doTest("ExtendedIsolationForest: Smoke Test", test.ExtendedIsolationForest.smoke)
