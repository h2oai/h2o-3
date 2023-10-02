setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.adaBoost.smoke <- function() {
    f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv"
    data <- h2o.importFile(f)
    
    # Set predictors and response; set response as a factor
    data["CAPSULE"] <- as.factor(data["CAPSULE"])
    predictors <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    response <- "CAPSULE"
    
    # Train the AdaBoost model
    h2o_adaboost <- h2o.adaBoost(x = predictors, y = response, training_frame = data, seed = 1234)
    expect_equal(is.null(h2o_adaboost), FALSE)
}

doTest("adaBoost: Smoke Test", test.adaBoost.smoke)
