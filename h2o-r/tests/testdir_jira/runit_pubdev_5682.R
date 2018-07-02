setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.CoxPH.nostop <- function() {
    cancer.hex <- as.h2o(cancer)
    X <- c("age", "sex", "meal.cal")

    expect_error(
        h2o.coxph(x = X, stop_column = "time", training_frame = cancer.hex),
        'argument "event_column" is missing, with no default'
    )

    expect_error(
        h2o.coxph(x = X, training_frame = cancer.hex, event_column = "status"),
        'argument "stop_column" must be a column name or an index'
    )

    model <- h2o.coxph(x = X, training_frame = cancer.hex, event_column = "status", stop_column = "time")
    expect_equal(model@model$formula, "Surv(time, status) ~ age + sex + meal.cal")
}

doTest("PUBDEV-5682: CoxPH should require parameter stop_column", test.CoxPH.nostop)