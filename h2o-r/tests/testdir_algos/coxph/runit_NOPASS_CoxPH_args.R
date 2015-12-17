setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.CoxPH.args <- function() {
  data(bladder)
  bladder.h2o <- as.h2o(bladder, destination_frame = "bladder.h2o")
  bladder0.h2o <- as.h2o(bladder[bladder$event == 0,], destination_frame = "bladder0.h2o")
  
  Log.info("H2O Cox PH x Argument")
  checkException(h2o.coxph(x = "foo",              y = c("stop", "event"), training_frame = bladder.h2o), silent = TRUE)
  checkException(h2o.coxph(x = c("number", "foo"), y = c("stop", "event"), training_frame = bladder.h2o), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder0.h2o), silent = TRUE)
  
  Log.info("H2O Cox PH y Argument")
  checkException(h2o.coxph(x = "foo",    y = c("foo", "event"), training_frame = bladder.h2o), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "foo"),  training_frame = bladder.h2o), silent = TRUE)
  
  Log.info("H2O Cox PH training_frame Argument")
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder), silent = TRUE)
  
  Log.info("H2O Cox PH init Control Argument")
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, init = NULL), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, init = NA_real_), silent = TRUE)
  
  Log.info("H2O Cox PH lre Control Argument")
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, lre = -1), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, lre = NULL), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, lre = NA_real_), silent = TRUE)
  
  Log.info("H2O Cox PH iter.max Control Argument")
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, iter.max = -1), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, iter.max = NULL), silent = TRUE)
  checkException(h2o.coxph(x = "number", y = c("stop", "event"), training_frame = bladder.h2o, iter.max = NA_integer_), silent = TRUE)
}

doTest("Cox PH Model Test: Function Arguments", test.CoxPH.args)
