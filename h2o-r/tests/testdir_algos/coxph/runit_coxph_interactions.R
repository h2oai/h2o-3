setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.cancer_interactions <- function() {
    fit <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = cancer, ties = "efron")
    print(fit)

    hex.fit.1 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                          event_column = "status", stop_column = "time", ties = "efron",
                          training_frame = as.h2o(cancer))
    print(hex.fit.1)

    coef.r <- fit$coefficients
    names(coef.r) <- gsub(":", "_", x = names(coef.r)) # H2O separates with '_' instead

    coef.hex.1 <- .as.survival.coxph.model(hex.fit.1@model)$coef[names(coef.r)] # reorder

    expect_equal(coef.r, coef.hex.1)
}

doTest("CoxPH: Interaction Test", test.CoxPH.cancer_interactions)