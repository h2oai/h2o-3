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
    
    # Case 4: only numeric, same weights
    tstdata.4 <- cancer
    tstdata.4$weights <- rep(0.123, nrow(tstdata.4))

    print(head(tstdata.4))
    print(tail(tstdata.4))

    tstdata.hex.4 <- as.h2o(tstdata.4)
    fit.4 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.4, weights = weights, ties = "efron")
    hex.fit.4 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", weights_column="weights", training_frame = tstdata.hex.4)
    hex.lp.4 <- pred(hex.fit.4, tstdata.hex.4)

    expect_equal(fit.4$linear.predictors, hex.lp.4, scale = 1, tolerance = 1e-3)
    expect_equal(fit.3$linear.predictors, hex.lp.4, scale = 1, tolerance = 1e-3)
    
    # Case 5: only numeric, random weights
    tstdata.5 <- cancer
    tstdata.5$weights <- runif(nrow(tstdata.5), min=0.01, max=2.0)

    tstdata.hex.5 <- as.h2o(tstdata.5)
    fit.5 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.5, weights = weights, ties = "efron")
    hex.fit.5 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", weights_column="weights", training_frame = tstdata.hex.5)
    hex.lp.5 <- pred(hex.fit.5, tstdata.hex.5)

    expect_equal(fit.5$linear.predictors, hex.lp.5, scale = 1, tolerance = 1e-3)

    # Case 6: categoricals, numeric + interactions... and random weights
    tstdata.6 <- cancer
    tstdata.6$sex <- as.factor(tstdata.6$sex)
    tstdata.6$weights <- runif(nrow(tstdata.6), min=0.01, max=9.0)
    tstdata.hex.6 <- as.h2o(tstdata.6)
    fit.6 <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = tstdata.6, ties = "efron", weights = weights)
    hex.fit.6 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.6,
                           weights_colum = "weights" )
    hex.lp.6 <- pred(hex.fit.6, tstdata.hex.6)

    expect_equal(fit.6$linear.predictors, hex.lp.6, scale = 1, tolerance = 1e-3)

    # Case 7: categoricals, numeric + interactions... and random weights... and breslow
    tstdata.7 <- cancer
    tstdata.7$sex <- as.factor(tstdata.7$sex)
    tstdata.7$weights <- runif(nrow(tstdata.7), min=0.01, max=9.0)
    tstdata.hex.7 <- as.h2o(tstdata.7)
    fit.7 <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = tstdata.7, weights = weights, ties = "breslow")
    hex.fit.7 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", ties = "breslow", weights_column = "weights",
                           training_frame = tstdata.hex.7)
    hex.lp.7 <- pred(hex.fit.7, tstdata.hex.7)

    expect_equal(fit.7$linear.predictors, hex.lp.7, scale = 1, tolerance = 1e-3)
}

doTest("CoxPH: Predict Test", test.CoxPH.predict)
