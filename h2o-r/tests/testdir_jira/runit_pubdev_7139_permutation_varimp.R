setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.permutation.varimp <- function(){
    Log.info("Training glm...")
    pros.train <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))

    pros.glm <- h2o.glm(x = 3:9, y = 2, training_frame = pros.train, family = "binomial")

    Log.info("Calculating Permutation Variable Importance...")
    permutation_varimp <- h2o.permutation_varimp(pros.glm, pros.train, metric = "MSE")

    h2o.isnumeric(permutation_varimp[3])
}

doTest("Testing Permutation Feature Importance", test.permutation.varimp)
