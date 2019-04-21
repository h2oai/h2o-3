setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.predict <- function() {
    pred <- function(model, data) {
        hex.lp <- h2o.predict(model, data)
        lp <- as.data.frame(hex.lp)$lp
        lp[!is.na(lp)]
    }

    # Case 1: categoricals, numeric + interactions
    tstdata.1 <- cancer
    tstdata.1$sex <- as.factor(tstdata.1$sex)
    tstdata.hex.1 <- as.h2o(tstdata.1)
    fit.1 <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = tstdata.1, ties = "efron")
    hex.fit.1 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.1)
    hex.lp.1 <- pred(hex.fit.1, tstdata.hex.1)

    expect_equal(fit.1$linear.predictors, hex.lp.1, scale = 1, tolerance = 1e-3)

    # Case 2: categoricals, numeric
    tstdata.2 <- cancer
    tstdata.2$sex <- as.factor(tstdata.2$sex)
    tstdata.hex.2 <- as.h2o(tstdata.2)
    fit.2 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.2, ties = "efron")
    hex.fit.2 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.2)
    hex.lp.2 <- pred(hex.fit.2, tstdata.hex.2)

    expect_equal(fit.2$linear.predictors, hex.lp.2, scale = 1, tolerance = 1e-3)

    # Case 3: only numeric
    tstdata.3 <- cancer
    tstdata.hex.3 <- as.h2o(tstdata.3)
    fit.3 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.3, ties = "efron")
    hex.fit.3 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.3)
    hex.lp.3 <- pred(hex.fit.3, tstdata.hex.3)

    expect_equal(fit.3$linear.predictors, hex.lp.3, scale = 1, tolerance = 1e-3)
}

doTest("CoxPH: Predict Test", test.CoxPH.predict)