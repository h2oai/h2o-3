setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.predict <- function() {

    pred.r <- function(fit, tstdata) {
        fit.pred <- predict(fit, tstdata, type = "lp")
        names(fit.pred) <- NULL
        return(fit.pred)
    }

    pred.h2o <- function(model, data) {
        hex.lp <- h2o.predict(model, data)
        as.data.frame(hex.lp)$lp
    }

    check.pred <- function(r.model, hex.model, r.tstdata, hex.tstdata) {
        fit.pred <- pred.r(r.model, r.tstdata)
        hex.lp <- pred.h2o(hex.model, hex.tstdata)
        expect_equal(fit.pred, hex.lp, tolerance = 1e-5, scale = 1)
    }
    
    check.concordance <- function (rModel, hexModel, data) {
        rConcordance <- unname(summary(rModel)$concordance)[1]
        perf <- h2o.performance(hexModel, data=heart.hex)
        hexConcordance <- perf@metrics$concordance
        expect_equal(rConcordance, hexConcordance, tolerance = 1e-5)
    }

    #heart.hex <- h2o.importFile(locate("smalldata/coxph_test/heart.csv"))
    heart.df <- heart #.data.frame(heart.hex)
    
    heart.hex <- as.h2o(heart.df)
    heart.hex$surgery <- as.factor(heart.hex$surgery)
    heart.hex$transplant <- as.factor(heart.hex$transplant)

    # simple case

    hexModel <- h2o.coxph(x="age", event_column="event",
                          stop_column="stop", training_frame=heart.hex, ties="breslow")
    rModel <- survival::coxph(Surv(stop, event) ~ age, data = heart.df, ties="breslow")

    check.pred(rModel, hexModel, heart.df, heart.hex)
    check.concordance(rModel, hexModel, heart.hex)

    # with multiple columns

    hexModel <- h2o.coxph(x=c("age", "year", "surgery", "transplant" ), event_column="event",
                          stop_column="stop", training_frame=heart.hex, ties="breslow")
    rModel <- survival::coxph(Surv(stop, event) ~ age  + year + surgery + transplant, data = heart.df, ties="breslow")

    check.pred(rModel, hexModel, heart.df, heart.hex)
    check.concordance(rModel, hexModel, heart.hex)

#   # with multiple columns and interactions

    hexModel <- h2o.coxph(x=c("age", "year", "surgery", "transplant" ), event_column="event",
                          stop_column="stop", training_frame=heart.hex, ties="breslow",
                          interaction_pairs=list(c("year", "surgery"), c("transplant", "surgery")))
    rModel <- survival::coxph(Surv(stop, event) ~ age  + year + surgery + transplant + year:surgery + transplant:surgery, 
                              data = heart.df, ties="breslow")

    check.pred(rModel, hexModel, heart.df, heart.hex)
    check.concordance(rModel, hexModel, heart.hex)

    # with multiple columns and interactions and stratification

    hexModel <- h2o.coxph(x=c("age", "year", "transplant"), event_column="event",
                          stop_column="stop", training_frame=heart.hex, ties="efron",
                          interaction_pairs=list(c("year", "transplant")), stratify_by = "surgery")
    rModel <- survival::coxph(Surv(stop, event) ~ age + year + transplant + year:transplant + strata(surgery), 
                              data = heart.df, ties="efron")

    check.pred(rModel, hexModel, heart.df, heart.hex)
    check.concordance(rModel, hexModel, heart.hex)

    # with multiple columns and interactions and multiple stratification

    hexModel <- h2o.coxph(x=c("age", "year", "transplant"), event_column="event",
                          stop_column="stop", training_frame=heart.hex, ties="efron",
                          interaction_pairs=list(c("year", "transplant")), stratify_by = c("surgery", "transplant"))
    rModel <- survival::coxph(Surv(stop, event) ~ age + year + transplant + year:transplant + strata(surgery) + strata(transplant),
                              data = heart.df, ties="efron")

    check.pred(rModel, hexModel, heart.df, heart.hex)
    check.concordance(rModel, hexModel, heart.hex)   
}

doTest("CoxPH: Predict Test", test.CoxPH.predict)
