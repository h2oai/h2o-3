setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.DRF.contribs_bernoulli <- function() {
    
    # Check using all columns as input
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_hex$CAPSULE <- as.factor(prostate_hex$CAPSULE)
    
    drf_model <- h2o.randomForest(training_frame = prostate_hex, y = "CAPSULE", seed=1234)
    
    predicted <- as.data.frame(h2o.predict(drf_model, prostate_hex))
    contributions <- as.data.frame(h2o.predict_contributions(drf_model, prostate_hex))

    p1_using_contributions <- rowSums(contributions)
    
    expect_equal(predicted$p1, p1_using_contributions, tolerance = 1e-6)
    
    # Check using all columns as input except "AGE"
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_hex$CAPSULE <- as.factor(prostate_hex$CAPSULE)

    drf_model <- h2o.randomForest(training_frame = prostate_hex, x=setdiff(names(prostate_hex), c("AGE", "CAPSULE")), y = "CAPSULE", seed=1234)

    predicted <- as.data.frame(h2o.predict(drf_model, prostate_hex))
    contributions <- as.data.frame(h2o.predict_contributions(drf_model, prostate_hex))

    p1_using_contributions <- rowSums(contributions)
    
    expect_equal(predicted$p1, p1_using_contributions, tolerance = 1e-6)

    # Check using only "AGE"
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate_hex$CAPSULE <- as.factor(prostate_hex$CAPSULE)

    drf_model <- h2o.randomForest(training_frame = prostate_hex, x = "AGE", y = "CAPSULE", seed=1234)

    predicted <- as.data.frame(h2o.predict(drf_model, prostate_hex))
    contributions <- as.data.frame(h2o.predict_contributions(drf_model, prostate_hex))

    p1_using_contributions <- rowSums(contributions)

    expect_equal(predicted$p1, p1_using_contributions, tolerance = 1e-6)
}

doTest("DRF Test: Make sure DRF contributions correspond to predictions for binomial models", test.DRF.contribs_bernoulli)
