setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.strata_inter <- function() {
    data(bladder)
    bladder1 <- bladder[bladder$enum < 5, ]
    bladder1$enum <- as.factor(bladder1$enum)
    bladder1.hex <- as.h2o(bladder1, destination_frame = "bladder1.hex")

    # In R
    fit <- coxph(Surv(stop, event) ~ rx + number + size + rx:enum + number:enum + size:enum + strata(enum), data = bladder1)
    print(fit)

    # In H2O
    bladder1_model <- h2o.coxph(x = c("rx", "size", "number"), event_column = "event", stop_column = "stop",
                                training_frame = bladder1.hex,
                                interaction_pairs = list(c("rx", "enum"), c("number", "enum"), c("size", "enum")),
                                stratify_by = "enum")
    print(bladder1_model)

    lp.r <- predict(fit, newdata = bladder1, type = "lp", na.action = na.exclude)
    names(lp.r) <- NULL
    lp.hex <- as.data.frame(h2o.predict(bladder1_model, bladder1.hex))$lp
    names(lp.hex) <- NULL

    expect_equal(lp.r, lp.hex)
}

doTest("CoxPH: Test Stratification with Interactions", test.CoxPH.strata_inter)