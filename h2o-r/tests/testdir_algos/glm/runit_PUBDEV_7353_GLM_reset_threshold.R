setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test <- function() {
    Log.info("Importing prostate.csv data...\n")
    prostate = h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), "prostate.hex")
    prostate.sum = summary(prostate)
    print(prostate.sum)

    prostate = na.omit(prostate)
    prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
                            training_frame = prostate, family = "binomial",
                            nfolds = 0, alpha = 0.5, lambda_search = FALSE)
    print("Get performance")
    perf_old = h2o.performance(prostate_glm, prostate)
    print(perf_old)
    print("Reset threshold")
    old_threshold = h2o.reset_threshold(prostate_glm, 0.9)
    print(old_threshold)
    print("Get performance with new threshold")
    reseted_model = h2o.getModel(prostate_glm@model_id)
    perf_new = h2o.performance(reseted_model, prostate)
    print(perf_new)

    pred = h2o.predict(prostate_glm, prostate)
    pred_reseted = h2o.predict(reseted_model, prostate)
    #expect_error(expect_equal(pred, pred_reseted))
    expect_equal(pred, pred_reseted)
    print(pred)
    print(pred_reseted)
 }

doTest("Test threshold", test)
