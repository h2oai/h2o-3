setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.XGBoost.interaction <- function() {
    Log.info("Importing prostate.csv data...\n")
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
    Log.info("Converting CAPSULE and RACE columns to factors...\n")
    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)
    Log.info("Summary of prostate.csv from H2O:\n")
    print(summary(prostate.hex))

    # Train H2O XGBoost Model:
    prostate.h2o <- h2o.xgboost(x = 3:9, y = "CAPSULE",
                                training_frame = prostate.hex,
                                distribution = "bernoulli",
                                ntrees = 5,
                                max_depth = 2,
                                min_rows = 10,
                                interaction_constraints = list(list("AGE", "DPROS"), list("RACE", "PSA", "VOL")),
                                categorical_encoding="AUTO")
    
}

doTest("XGBoost Test: prostate.csv with interaction constraints.", test.XGBoost.interaction)
