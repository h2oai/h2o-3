setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.contribs_sanity_check <- function() {
    housing_hex <- h2o.importFile(locate("smalldata/gbm_test/BostonHousing.csv"))

    gbm_model <- h2o.gbm(training_frame = housing_hex, y = "medv", seed = 42)

    predicted <- as.data.frame(h2o.predict(gbm_model, housing_hex))
    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, housing_hex))

    expect_equal(ncol(contributions), ncol(housing_hex))
    expect_equal(rowSums(contributions), predicted$predict, tolerance = 1e-6)
}

doTest("GBM Test: Make sure GBM contributions sum-up to predictions for regression models", test.GBM.contribs_sanity_check)
