setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test_threshold <- function() {
    Log.info("Importing prostate.csv data...\n")
    prostate <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), "prostate.hex")
    prostate <- na.omit(prostate)

    prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
    training_frame = prostate, family = "binomial",
    nfolds = 0, alpha = 0.5, lambda_search = FALSE)

    print("Predict")
    pred <- h2o.predict(prostate_glm, prostate)

    print("Reset the threshold and get the old one")
    new_threshold <- 0.903690735680182
    old_threshold <- h2o.reset_threshold(prostate_glm, new_threshold)
    print(paste("old_threshold:", old_threshold, "new_threshold:", new_threshold))

    print("Get model with the new threshold")
    reseted_model <- h2o.getModel(prostate_glm@model_id)

    print("Compare thresholds")
    expect_equal(old_threshold, prostate_glm@model$default_threshold)
    expect_equal(new_threshold, reseted_model@model$default_threshold)

    print("Predict with the reset model")
    pred_reset <- h2o.predict(reseted_model, prostate)
    print("Check predictions based on thresholds")
    for (i in 1:nrow(pred)) {
        if(pred[i,3] >= old_threshold & pred[i,3] < new_threshold){
            expect_true(pred[i, 1] != pred_reset[i, 1])
        } else {
            expect_equal(pred[i, 1], pred_reset[i, 1])
        }
    }
}

doTest("Test reset model threshold", test_threshold)
