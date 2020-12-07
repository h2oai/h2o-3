setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# test that plot(performance) generates a curve plot
test.curve_plot <- function() {
    # Importing prostate.csv data
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
    # Converting CAPSULE and RACE columns to factors
    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)

    # Train H2O GBM Model:
    prostate.gbm <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, distribution = "bernoulli")
    
    # Get performance object
    perf = h2o.performance(prostate.gbm)

    # Plot ROC curve
    plot(perf, type='roc')
    
    # Plot PR curve
    plot(perf, type='pr')
}

doTest("Plot ROC and PR curves", test.curve_plot)

