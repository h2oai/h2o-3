setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.CoxPH.predict <- function() {

    heart.hex <- function () {
        result <- as.h2o(heart)
        result$surgery <- as.factor(result$surgery)
        result$transplant <- as.factor(result$transplant)
        return(result)
    }
    
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
        expect_equal(fit.pred - mean(fit.pred), hex.lp, tolerance = 1e-5, scale = 1)
    }
    
    check.concordance <- function (rModel, hexModel, data, tolerance = 1e-3) {
        perf <- h2o.performance(hexModel, data=data)
        
    	rConcordance <- unname(summary(rModel)$concordance)[1]
        hexConcordance <- perf@metrics$concordance
        expect_equal(rConcordance, hexConcordance, tolerance = tolerance, scale = 1)
        
        hexConcordantCount <- perf@metrics$concordant
        hexDiscordantCount <- perf@metrics$discordant
        hexTiedCount <- perf@metrics$tied_y
        
        expect_equal(hexConcordance, (hexConcordantCount + 0.5 * hexTiedCount) / (hexConcordantCount + hexDiscordantCount + hexTiedCount))
    }


    multiple.columns <- function() {
        data.hex <- heart.hex()
        data.r <- heart
        
        hexModel <- h2o.coxph(x = c("age", "year", "surgery", "transplant"), event_column = "event",
                              stop_column = "stop", training_frame = data.hex, ties = "breslow")
        rModel <- survival::coxph(Surv(stop, event) ~ age + year + surgery + transplant, data = data.r, ties = "breslow")
        check.pred(rModel, hexModel, data.r, data.hex)
        check.concordance(rModel, hexModel, data.hex)
    }

    simple.case <- function() {
        data.hex <- heart.hex()
        data.r <- heart
        
        hexModel <- h2o.coxph(x = "age", event_column = "event",
                              stop_column = "stop", training_frame = data.hex, ties = "breslow")
        rModel <- survival::coxph(Surv(stop, event) ~ age, data = data.r, ties = "breslow")
        check.pred(rModel, hexModel, data.r, data.hex)
        check.concordance(rModel, hexModel, data.hex)
    } 
    
    multiple.columns.and.interactions <- function() {
        data.hex <- heart.hex()
        data.r <- heart
        
        hexModel <- h2o.coxph(x=c("age", "year", "surgery", "transplant" ), event_column="event",
                              stop_column="stop", training_frame=data.hex, ties="breslow",
                              interaction_pairs=list(c("year", "surgery"), c("transplant", "surgery")))
        rModel <- survival::coxph(Surv(stop, event) ~ age  + year + surgery + transplant + year:surgery + transplant:surgery,
                                  data = data.r, ties="breslow")
        
        check.pred(rModel, hexModel, data.r, data.hex)
        check.concordance(rModel, hexModel, data.hex)
    }
    
    multiple.columns.and.interactions.with.strata <- function() {
        data.hex <- heart.hex()
        data.r <- heart

        hexModel <- h2o.coxph(x=c("age", "year", "transplant"), event_column="event",
                              stop_column="stop", training_frame=data.hex, ties="efron",
                              interaction_pairs=list(c("year", "transplant")), stratify_by = "surgery")
        rModel <- survival::coxph(Surv(stop, event) ~ age + year + transplant + year:transplant + strata(surgery),
                                  data = data.r, ties="efron")
        
        check.pred(rModel, hexModel, data.r, data.hex)
        check.concordance(rModel, hexModel, data.hex)
    } 
    
    multiple.columns.and.interactions.with.multiple.strata <- function() {
        data.hex <- heart.hex()
        data.r <- heart

        hexModel <- h2o.coxph(x=c("age", "year", "transplant"), event_column="event",
                              stop_column="stop", training_frame=data.hex, ties="efron",
                              interaction_pairs=list(c("year", "transplant")), stratify_by = c("surgery", "transplant"))
        rModel <- survival::coxph(Surv(stop, event) ~ age + year + transplant + year:transplant + strata(surgery) + strata(transplant),
                                  data=data.r, ties="efron")
        
        check.pred(rModel, hexModel, data.r, data.hex)
        check.concordance(rModel, hexModel, data.hex, tolerance = 1.2E-3)
    }

    simple.case()
    multiple.columns()
    multiple.columns.and.interactions()
    multiple.columns.and.interactions.with.strata()
    multiple.columns.and.interactions.with.multiple.strata()
}

doTest("CoxPH: Predict Test", test.CoxPH.predict)
