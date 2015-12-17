setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.bladder <- function() {
  data(bladder)
  bladder$rx   <- as.factor(bladder$rx)
  bladder$enum <- as.factor(bladder$enum)
  bladder.h2o  <- as.h2o(bladder, destination_frame = "bladder.h2o")
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 1 numeric predictor\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "size", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o")
  bladder.coxph <- coxph(Surv(stop, event) ~ size, data = bladder)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph, tolerance = 1e-7)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 1 categorical predictor\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "enum", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum, data = bladder)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 4 predictors\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx", "number", "size"), y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum + rx + number + size, data = bladder)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 4 predictors and case weights\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx", "number", "size"), y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", weights = "id")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum + rx + number + size, data = bladder, weights = bladder$id)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 2 predictors and 1 offset\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx"), y = c("stop", "event"), training_frame = bladder.h2o, model_id = "bladmod.h2o",
              offset = "size")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum + rx + offset(size), data = bladder)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 2 predictors and 2 offsets\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx"), y = c("stop", "event"), training_frame = bladder.h2o, model_id = "bladmod.h2o",
              offset = c("number", "size"), weights = "id")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum + rx + offset(number) + offset(size), data = bladder,
                         weights = bladder$id)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; init = 0.2\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "size", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", init = - 0.1)
  bladder.coxph <- coxph(Surv(stop, event) ~ size, data = bladder, init = - 0.1)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; iter.max = 1\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "size", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", control = h2o.coxph.control(iter.max = 1))
  bladder.coxph <- coxph(Surv(stop, event) ~ size, data = bladder,
                         control = coxph.control(iter.max = 1))
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; 1 numeric predictor\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "size", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", ties = "breslow")
  bladder.coxph <-
    coxph(Surv(stop, event) ~ size, data = bladder, ties = "breslow")
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph, tolerance = 1e-7)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Efron's Approximation; 1 categorical predictor\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "enum", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", ties = "breslow")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum, data = bladder, ties = "breslow")
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; 4 predictors\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx", "number", "size"), y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", ties = "breslow")
  bladder.coxph <-
    coxph(Surv(stop, event) ~ enum + rx + number + size, data = bladder, ties = "breslow")
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; 4 predictors and case weights\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx", "number", "size"), y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", weights = "id", ties = "breslow")
  bladder.coxph <-
    coxph(Surv(stop, event) ~ enum + rx + number + size, data = bladder, weights = bladder$id, ties = "breslow")
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; 2 predictors and 1 offset\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx"), y = c("stop", "event"), training_frame = bladder.h2o, model_id = "bladmod.h2o",
              offset = "size", ties = "breslow")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum + rx + offset(size), data = bladder, ties = "breslow")
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; 2 predictors and 2 offsets\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = c("enum", "rx"), y = c("stop", "event"), training_frame = bladder.h2o, model_id = "bladmod.h2o",
              offset = c("number", "size"), weights = "id", ties = "breslow")
  bladder.coxph <- coxph(Surv(stop, event) ~ enum + rx + offset(number) + offset(size), data = bladder,
                         weights = bladder$id, ties = "breslow")
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph, tolerance = 1e-7)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; init = 0.2\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "size", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", ties = "breslow", init = - 0.1)
  bladder.coxph <-
    coxph(Surv(stop, event) ~ size, data = bladder, ties = "breslow",
          init = - 0.1)
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
  
  Log.info("H2O Cox PH Model of bladder Data Set using Breslow's Approximation; iter.max = 1\n")
  bladder.coxph.h2o <-
    h2o.coxph(x = "size", y = c("stop", "event"), training_frame = bladder.h2o,
              model_id = "bladmod.h2o", ties = "breslow",
              control = h2o.coxph.control(iter.max = 1))
  bladder.coxph <-
    coxph(Surv(stop, event) ~ size, data = bladder, ties = "breslow",
          control = coxph.control(iter.max = 1))
  checkCoxPHModel(bladder.coxph.h2o, bladder.coxph)
}

doTest("Cox PH Model Test: Bladder", test.CoxPH.bladder)
