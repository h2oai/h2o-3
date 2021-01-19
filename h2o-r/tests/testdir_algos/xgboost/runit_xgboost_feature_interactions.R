setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.feature_interactions <- function() {
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
    response <- "RACE"
    ignored_columns=c("ID")
    features <- setdiff(setdiff(names(prostate.hex), response), ignored_columns)
    
    prostate.h2o <- h2o.xgboost( y = response, x = features, training_frame = prostate.hex)
    
    feature_interactions <- h2o.feature_interaction(prostate.h2o, 2, 100, -1)
    
    print(feature_interactions)
    expect_equal(length(feature_interactions), 11)

}

doTest("XGBoost Test: prostate.csv for feature interactions", test.XGBoost.feature_interactions)
