setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.heart <- function() {
  data(heart)
  heart$start <- as.integer(heart$start)
  heart$stop  <- as.integer(heart$stop)
  heart.h2o <- as.h2o(heart, key = "heart.h2o")
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 1 numeric predictor\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "age", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o")
  heart.coxph <- coxph(Surv(start, stop, event) ~ age, data = heart)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 1 categorical predictor\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "transplant", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant, data = heart)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 4 predictors\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "age", "year", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant + age + year + surgery, data = heart)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 4 predictors and case weights\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "age", "year", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", weights = "id")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant + age + year + surgery, data = heart, weights = heart$id)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 2 predictors and 1 offset\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", offset = "age")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant + surgery + offset(age), data = heart)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 2 predictors and 2 offsets\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", weights = "id", offset = c("age", "year"))
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant + surgery + offset(age) + offset(year), data = heart,
                       weights = heart$id)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; init = 0.05\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "age", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", init = 0.05)
  heart.coxph <- coxph(Surv(start, stop, event) ~ age, data = heart, init = 0.05)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; iter.max = 1\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "age", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", control = h2o.coxph.control(iter.max = 1))
  heart.coxph <- coxph(Surv(start, stop, event) ~ age, data = heart,
                       control = coxph.control(iter.max = 1))
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; 1 numeric predictor\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "age", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", ties = "breslow")
  heart.coxph <-
    coxph(Surv(start, stop, event) ~ age, data = heart, ties = "breslow")
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Efron's Approximation; 1 categorical predictor\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "transplant", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", ties = "breslow")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant, data = heart, ties = "breslow")
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; 4 predictors\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "age", "year", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", ties = "breslow")
  heart.coxph <-
    coxph(Surv(start, stop, event) ~ transplant + age + year + surgery, data = heart, ties = "breslow")
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; 4 predictors and case weights\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "age", "year", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", weights = "id", ties = "breslow")
  heart.coxph <-
    coxph(Surv(start, stop, event) ~ transplant + age + year + surgery, data = heart,
          weights = heart$id, ties = "breslow")
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; 2 predictors and 1 offset\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", offset = "age", ties = "breslow")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant + surgery + offset(age), data = heart,
                       ties = "breslow")
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; 2 predictors and 2 offsets\n")
  heart.coxph.h2o <-
    h2o.coxph(x = c("transplant", "surgery"), y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", weights = "id", offset = c("age", "year"), ties = "breslow")
  heart.coxph <- coxph(Surv(start, stop, event) ~ transplant + surgery + offset(age) + offset(year), data = heart,
                       weights = heart$id, ties = "breslow")
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; init = 0.05\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "age", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", ties = "breslow", init = 0.05)
  heart.coxph <-
    coxph(Surv(start, stop, event) ~ age, data = heart, ties = "breslow",
          init = 0.05)
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
  
  Log.info("H2O Cox PH Model of heart Data Set using Breslow's Approximation; iter.max = 1\n")
  heart.coxph.h2o <-
    h2o.coxph(x = "age", y = c("start", "stop", "event"), training_frame = heart.h2o,
              model_id = "heartmod.h2o", ties = "breslow",
              control = h2o.coxph.control(iter.max = 1))
  heart.coxph <-
    coxph(Surv(start, stop, event) ~ age, data = heart, ties = "breslow",
          control = coxph.control(iter.max = 1))
  checkCoxPHModel(heart.coxph.h2o, heart.coxph)
}

doTest("Cox PH Model Test: Heart", test.CoxPH.heart)
