setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.cancer <- function() {
    fit <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = cancer, ties = "efron")
    print(fit)

    hex.fit <- h2o.coxph(x = c("age", "sex", "meal.cal"), event_column = "status", stop_column = "time", ties = "efron",
                         training_frame = as.h2o(cancer))
    print(hex.fit)

    expect_equal(Surv(time, status) ~ age + sex + meal.cal, .as.survival.coxph.model(hex.fit@model)$call)
    expect_equal(fit$coef, .as.survival.coxph.model(hex.fit@model)$coef, tolerance = 1e-8)
    expect_equal(fit$var, .as.survival.coxph.model(hex.fit@model)$var, tolerance = 1e-8)
}

doTest("CoxPH: Cancer Test", test.CoxPH.cancer)
