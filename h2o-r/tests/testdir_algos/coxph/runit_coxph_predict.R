setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


pred.r <- function(fit, tstdata) {
    fit.pred <- predict(fit, tstdata, type = "lp")
    names(fit.pred) <- NULL
    return(fit.pred)
}

pred.h2o <- function(model, data) {
    hex.lp <- h2o.predict(model, data)
    as.data.frame(hex.lp)$lp
}

compare.results <- function(fit, hex.fit, tstdata, tstdata.hex) {
    fit.pred <- pred.r(fit, tstdata)
    hex.lp <- pred.h2o(hex.fit, tstdata.hex)
    expect_equal(fit.pred, hex.lp, tolerance = 1e-7, scale = 1)
}

cancer.with.sex <- function () {
    tstdata <- cancer
    tstdata$sex <- as.factor(tstdata$sex)
    tstdata
}

test.CoxPH.predict <- function() {

    ## Case 1: categoricals, numeric + interactions
    tstdata.1 <- cancer.with.sex()
    tstdata.hex.1 <- as.h2o(tstdata.1)
    
    fit.1 <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = tstdata.1, ties = "efron")
    hex.fit.1 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.1)

    compare.results(fit.1, hex.fit.1, tstdata.1, tstdata.hex.1)

    ## Case 2: categoricals, numeric
    tstdata.2 <- cancer.with.sex()
    tstdata.hex.2 <- as.h2o(tstdata.2)
    
    fit.2 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.2, ties = "efron")
    hex.fit.2 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.2)

    compare.results(fit.2, hex.fit.2, tstdata.2, tstdata.hex.2)
     
    ## Case 3: only numeric
    tstdata.3 <- cancer
    tstdata.hex.3 <- as.h2o(tstdata.3)
    
    fit.3 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.3, ties = "efron")
    hex.fit.3 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", training_frame = tstdata.hex.3)

    compare.results(fit.3, hex.fit.3, tstdata.3, tstdata.hex.3)
    
    ## Case 4: only numeric, same weights
    tstdata.4 <- cancer
    tstdata.4$weights <- rep(0.123, nrow(tstdata.4))
    tstdata.hex.4 <- as.h2o(tstdata.4)

    fit.4 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.4, weights = weights, ties = "efron")
    hex.fit.4 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", weights_column="weights", training_frame = tstdata.hex.4)

    compare.results(fit.4, hex.fit.4, tstdata.4, tstdata.hex.4)
    compare.results(fit.3, hex.fit.4, tstdata.3, tstdata.hex.4)
     
    ## Case 5: only numeric, random weights
    tstdata.5 <- cancer
    tstdata.5$weights <- runif(nrow(tstdata.5), min=0.01, max=2.0)
    tstdata.hex.5 <- as.h2o(tstdata.5)
    
    fit.5 <- coxph(Surv(time, status) ~ age + sex + meal.cal, data = tstdata.5, weights = weights, ties = "efron")
    hex.fit.5 <- h2o.coxph(x = c("age", "sex", "meal.cal"),
                           event_column = "status", stop_column = "time", ties = "efron", weights_column="weights", training_frame = tstdata.hex.5)
    compare.results(fit.5, hex.fit.5, tstdata.5, tstdata.hex.5)

    
    ## Case 6: categoricals, numeric + interactions... and random weights
    tstdata.6 <- cancer.with.sex()
    tstdata.6$weights <- runif(nrow(tstdata.6), min=0.01, max=9.0)
    tstdata.hex.6 <- as.h2o(tstdata.6)

    fit.6 <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = tstdata.6, 
                   weights = weights, ties = "efron")
    hex.fit.6 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time", 
                           weights_column = "weights", ties = "efron", training_frame = tstdata.hex.6)

    compare.results(fit.6, hex.fit.6, tstdata.6, tstdata.hex.6)

    ## Case 7: categoricals, numeric + interactions... and random weights... and breslow
    tstdata.7 <- cancer.with.sex()
    tstdata.7$weights <- runif(nrow(tstdata.7), min=0.01, max=9.0)
    tstdata.hex.7 <- as.h2o(tstdata.7)

    fit.7 <- coxph(Surv(time, status) ~ age + sex + meal.cal + age:meal.cal, data = tstdata.7, 
                   weights = weights,ties = "breslow")
    hex.fit.7 <- h2o.coxph(x = c("age", "sex", "meal.cal"), interaction_pairs = list(c("age", "meal.cal")),
                           event_column = "status", stop_column = "time",
                           weights_column = "weights", ties = "breslow", training_frame = tstdata.hex.7)

    compare.results(fit.7, hex.fit.7, tstdata.7, tstdata.hex.7)
  
    ## Case 8: categoricals, numeric... no weights... and breslow... and strata
    tstdata.8 <- cancer.with.sex()
    tstdata.hex.8 <- as.h2o(tstdata.8)
    
    fit.8 <- coxph(Surv(time, status) ~ age + meal.cal + strata(sex), data = tstdata.8, ties = "breslow")
    hex.fit.8 <- h2o.coxph(x = c("age", "meal.cal"),
                           event_column = "status",
                           stratify_by = "sex", stop_column = "time", ties = "breslow", training_frame = tstdata.hex.8)

    compare.results(fit.8, hex.fit.8, tstdata.8, tstdata.hex.8)
    
    ## Case 9: categoricals, numeric... weights... and breslow... and strata
    tstdata.9 <- cancer.with.sex()
    tstdata.9$weights <- runif(nrow(tstdata.9), min=0.01, max=9.0)
    tstdata.hex.9 <- as.h2o(tstdata.9)

    fit.9 <- coxph(Surv(time, status) ~ age + meal.cal + strata(sex), 
                        data = tstdata.9,
                        weights = weights,
                        ties = "breslow")
    hex.fit.9 <- h2o.coxph(x = c("age", "meal.cal"),
                           event_column = "status",
                           weights_column = "weights",
                           stratify_by = "sex", stop_column = "time", ties = "breslow", training_frame = tstdata.hex.9)

    compare.results(fit.9, hex.fit.9, tstdata.9, tstdata.hex.9) 
    
    ## Case 10: categoricals, numeric... weights... and efron... and interactions... and strata
    tstdata.10 <- cancer.with.sex()
    tstdata.10$weights <- runif(nrow(tstdata.10), min=0.01, max=10.0)
    tstdata.hex.10 <- as.h2o(tstdata.10)

    fit.10 <- coxph(Surv(time, status) ~ age + meal.cal + age:meal.cal + strata(sex), 
                        data = tstdata.10,
                        weights = weights,
                        ties = "efron")
    hex.fit.10 <- h2o.coxph(x = c("age", "meal.cal"),
                           event_column = "status",
                           weights_column = "weights", interaction_pairs = list(c("age", "meal.cal")),
                           stratify_by = "sex", stop_column = "time", ties = "efron", training_frame = tstdata.hex.10)

    compare.results(fit.10, hex.fit.10, tstdata.10, tstdata.hex.10)
}

doTest("CoxPH: Predict Test", test.CoxPH.predict)
