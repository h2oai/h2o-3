setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.contribs_bernoulli <- function() {
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_hex$CAPSULE <- as.factor(prostate_hex$CAPSULE) 
    
    gbm_model <- h2o.gbm(training_frame = prostate_hex, y = "CAPSULE")

    predicted <- as.data.frame(h2o.predict(gbm_model, prostate_hex))
    contributions <- as.data.frame(h2o.predict_contributions(gbm_model, prostate_hex))

    sigmoid <- function(x) {
        1 / (1 + exp(-x))
    }

    p1_using_contributions <- sigmoid(rowSums(contributions))

    expect_equal(predicted$p1, p1_using_contributions, tolerance = 1e-6) 
}

doTest("GBM Test: Make sure GBM contributions correspond to preditions for binomial models", test.GBM.contribs_bernoulli)
