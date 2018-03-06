setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.strata1 <- function() {
    fit <- coxph(Surv(futime, fustat) ~ age + strata(rx), data = ovarian)
    print(fit)

    ovarian.hex <- as.h2o(ovarian)
    ovarian.hex$rx <- as.factor(ovarian.hex$rx)
    hex.fit <- h2o.coxph(stop_column = "futime", event_column = "fustat", x = c("age", "rx"), stratify_by="rx", training_frame = ovarian.hex)
    print(hex.fit)

    expect_equal(fit$coef, .as.survival.coxph.model(hex.fit@model)$coef, tolerance = 1e-8, scale = 1)
    expect_equal(fit$var, .as.survival.coxph.model(hex.fit@model)$var, tolerance = 1e-8, scale = 1)

    lp <- h2o.predict(hex.fit, ovarian.hex)
    expect_equal(fit$linear.predict, as.data.frame(lp)$lp)
}

doTest("CoxPH: Test Stratification with 1 column", test.CoxPH.strata1)