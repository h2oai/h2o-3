setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.strata_inter <- function() {
    data(lung)

    # 1. interaction with strata
    fit <- coxph(Surv(time, status) ~ wt.loss + age:strata(sex) + strata(ph.ecog), data = lung)

    fit.coef <- fit$coefficients[c("wt.loss", "age:strata(sex)sex=1", "age:strata(sex)sex=2")]
    names(fit.coef) <- c("wt.loss", "age_sex.1", "age_sex.2")

    # 2. explicit interaction
    lung.ext <- lung
    lung.ext$age_sex.1 <- lung$age * (lung$sex == 1)
    lung.ext$age_sex.2 <- lung$age * (lung$sex == 2)

    fit.ext <- coxph(Surv(time, status) ~ wt.loss + age_sex.1 + age_sex.2 + strata(sex) + strata(ph.ecog), data = lung.ext)

    expect_equal(fit.coef, fit.ext$coefficients)

    # 3. explicit interaction in H2O
    lung.hex <- as.h2o(lung.ext)
    lung.hex$sex <- as.factor(lung.hex$sex)
    lung.hex$ph.ecog <- as.factor(lung.hex$ph.ecog)

    # 5. implicit interaction in H2O - should figure out when to force-enable "use all factor levels"
    fit.hex.implicit.2 <- h2o.coxph(stop_column = "time", event_column = "status",
                                   x = "wt.loss",
                                   interaction_pairs = list(c("age", "sex")),
                                   stratify_by = c("sex", "ph.ecog"),
                                   training_frame = lung.hex)

    hex.model <- .as.survival.coxph.model(fit.hex.implicit.2@model)
    expect_equal(Surv(time, status) ~ wt.loss + age:strata(sex) + strata(ph.ecog), hex.model$call)
    expect_equal(fit.coef, hex.model$coef[names(fit.coef)])

    lp.r <- predict(fit, newdata = lung, type = "lp", na.action = na.exclude)
    names(lp.r) <- NULL
    lp.hex <- as.data.frame(h2o.predict(fit.hex.implicit.2, lung.hex))$lp
    names(lp.hex) <- NULL

    expect_equal(lp.r, lp.hex)
}

doTest("CoxPH: Test Stratification with Interactions", test.CoxPH.strata_inter)