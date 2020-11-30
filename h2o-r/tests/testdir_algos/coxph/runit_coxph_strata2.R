setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.strata2 <- function() {
    data(pbc)

    pbc.ext <- pbc
    pbc.ext$log_bili <- log(pbc$bili)
    pbc.ext$log_protime <- log(pbc$protime)
    pbc.ext$log_albumin <- log(pbc$albumin)

    fit <- coxph(Surv(time, status == 2) ~ age + edema + log_bili + log_protime + log_albumin + strata(ascites), data = pbc.ext)
    print(fit)

    pbc.hex <- as.h2o(pbc.ext)
    pbc.hex$status <- pbc.hex$status == 2
    pbc.hex$ascites <- as.factor(pbc.hex$ascites)

    fit.hex <- h2o.coxph(stop_column = "time", event_column = "status",
                         x = c("age", "edema", "log_bili", "log_protime", "log_albumin", "ascites"),
                         stratify_by = "ascites", training_frame = pbc.hex)
    print(fit.hex)

    expect_equal(fit$coef, .as.survival.coxph.model(fit.hex@model)$coef, tolerance = 1e-6, scale = 1)
    expect_equal(fit$var, .as.survival.coxph.model(fit.hex@model)$var, tolerance = 1e-6, scale = 1)

    lp.r <- predict(fit, pbc.ext, na.action = na.exclude, type = "lp")
    names(lp.r) <- NULL
    lp.hex <- h2o.predict(fit.hex, pbc.hex)

    expect_equal(lp.r, as.data.frame(lp.hex)$lp)
}

doTest("CoxPH: Test Stratification with 1 strata columns (with NAs)", test.CoxPH.strata2)
