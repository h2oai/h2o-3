setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# test that varimp_plot() generates a plot
test.varimp_plot <- function() {
    # Importing prostate.csv data
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
    # Converting CAPSULE and RACE columns to factors
    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)

    # Train H2O GBM Model:
    prostate.gbm <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, distribution = "bernoulli")

    # plot variable importance for all and two features
    h2o.varimp_plot(prostate.gbm)
    h2o.varimp_plot(prostate.gbm, 2)

}

doTest("Plot Variable Importance", test.varimp_plot)

