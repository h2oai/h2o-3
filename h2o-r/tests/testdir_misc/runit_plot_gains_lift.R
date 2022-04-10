setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# test that plot(performance) generates a curve plot
test.gains_lift_plot <- function() {
    # Importing prostate.csv data
    prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
    # Converting CAPSULE and RACE columns to factors
    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)

    # Train H2O GBM Model:
    prostate.gbm <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, distribution = "bernoulli")
    
    # Get performance object
    perf <- h2o.performance(prostate.gbm)

    # Plot using metrics
    plot(perf, type='gains_lift')
    h2o.gains_lift_plot(perf) # type = "both" (default)
    h2o.gains_lift_plot(perf, type = "gains")
    h2o.gains_lift_plot(perf, type = "lift")

    # Plot using model
    plot(prostate.gbm, type='gains_lift')
    h2o.gains_lift_plot(prostate.gbm)
    h2o.gains_lift_plot(prostate.gbm, type = "gains")
    h2o.gains_lift_plot(prostate.gbm, type = "lift")
}

doTest("Plot Gains Lift", test.gains_lift_plot)

