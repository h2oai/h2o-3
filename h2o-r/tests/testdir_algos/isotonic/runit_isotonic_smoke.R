setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.isotonicregression.smoke <- function() {
    set.seed(1234)
    N <- 100
    x <- seq(N)
    y <- sample(-50:50, N, replace=TRUE) + 50 * log1p(x)
    train <- as.h2o(data.frame(x = x, y = y))
    
    isotonic <- h2o.isotonicregression(x = "x", y = "y", training_frame = train)
    print(isotonic)

    expect_equal(as.character(class(isotonic)), "H2ORegressionModel")

    # check the summary (and also that we didn't memorize everything)    
    summary_df <- as.data.frame(isotonic@model$model_summary)
    expected_summary <- data.frame(
        number_of_observations = N,
        number_of_thresholds = 30
    )
    expect_equal(summary_df, expected_summary, check.attributes=FALSE)

    # run GLM for comparison - isotonic should result in a better fit
    glm <- h2o.glm(x = "x", y = "y", training_frame = train)
    expect_true(h2o.rmse(isotonic) < h2o.rmse(glm))
}

doTest("IsotonicRegression: Smoke Test", test.isotonicregression.smoke)
