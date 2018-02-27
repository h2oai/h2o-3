setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.cancer_interactions_only <- function() {
    fit <- coxph(Surv(time, status) ~ age + sex + age:meal.cal, data = cancer, ties = "efron")
    print(fit)

    cancer.hex <- as.h2o(cancer)

    # Use explicit syntax to set "interactions_only"
    hex.fit.1 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                          interactions_only = c("meal.cal"),
                          event_column = "status", stop_column = "time", ties = "efron",
                          training_frame = cancer.hex)
    print(hex.fit.1)

    # Automatically derive "interactions_only" from "interaction_pairs" and "x"
    hex.fit.2 <- h2o.coxph(x = c("age", "sex"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", ties = "efron",
                           training_frame = cancer.hex)
    print(hex.fit.2)

    coef.r <- fit$coefficients
    names(coef.r) <- gsub(":", "_", x = names(coef.r)) # H2O separates with '_' instead

    coef.hex.1 <- .as.survival.coxph.model(hex.fit.1@model)$coef[names(coef.r)] # reorder
    coef.hex.2 <- .as.survival.coxph.model(hex.fit.2@model)$coef[names(coef.r)] # reorder

    expect_equal(coef.r, coef.hex.1)
    expect_equal(coef.r, coef.hex.2)
}

doTest("CoxPH: Interaction Test - column meal.cal only used in an interaction", test.CoxPH.cancer_interactions_only)