checkCoxPHModel <- function(myCoxPH.h2o, myCoxPH.r, tolerance = 1e-8, ...) {
  require(RUnit, quietly = TRUE)
  cat("H2O Cox Proportional Hazards Model\n")
  print(myCoxPH.h2o)
  cat("\nsurvival Package Cox Proportional Hazards Model\n")
  print(myCoxPH.r)
  checkEquals(myCoxPH.r$coefficients, myCoxPH.h2o@model$coefficients,
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(myCoxPH.r$var,          myCoxPH.h2o@model$var,
              tolerance = tolerance)
  checkEquals(myCoxPH.r$loglik,       myCoxPH.h2o@model$loglik,
              tolerance = tolerance)
  checkEquals(myCoxPH.r$score,        myCoxPH.h2o@model$score,
              tolerance = tolerance)
  checkTrue  (                        myCoxPH.h2o@model$iter >= 1L)
  if (myCoxPH.h2o@survfit$type == "counting")
    myCoxPH.r$means[] <- myCoxPH.h2o@model$means # survival::coxph generates unweighted means when a start time is supplied
  else
    checkEquals(myCoxPH.r$means,      myCoxPH.h2o@model$means,
                tolerance = tolerance, check.attributes = FALSE)
  checkEquals(myCoxPH.r$method,       myCoxPH.h2o@model$method)
  checkEquals(myCoxPH.r$n,            myCoxPH.h2o@model$n)
  checkEquals(myCoxPH.r$nevent,       myCoxPH.h2o@model$nevent)
  checkEquals(myCoxPH.r$wald.test,    myCoxPH.h2o@model$wald.test,
              tolerance = sqrt(tolerance), check.attributes = FALSE)
  
  summaryCoxPH.h2o <- summary(myCoxPH.h2o)
  summaryCoxPH.r   <- summary(myCoxPH.r)
  cat("\nH2O Cox Proportional Hazards Model Summary\n")
  print(summaryCoxPH.h2o)
  cat("\nsurvival Package Cox Proportional Hazards Model Summary\n")
  print(summaryCoxPH.r)
  checkEquals(summaryCoxPH.r$n,            summaryCoxPH.h2o@summary$n)
  checkEquals(summaryCoxPH.r$loglik,       summaryCoxPH.h2o@summary$loglik,
              tolerance = tolerance)
  checkEquals(summaryCoxPH.r$nevent,       summaryCoxPH.h2o@summary$nevent)
  checkEquals(summaryCoxPH.r$coefficients, summaryCoxPH.h2o@summary$coefficients,
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(summaryCoxPH.r$conf.int,     summaryCoxPH.h2o@summary$conf.int,
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(summary(myCoxPH.r,   conf.int = 0.99)$conf.int,
              summary(myCoxPH.h2o, conf.int = 0.99)@summary$conf.int,
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(summary(myCoxPH.r,   conf.int = 0.90, scale = 1.2)$conf.int,
              summary(myCoxPH.h2o, conf.int = 0.90, scale = 1.2)@summary$conf.int,
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(summaryCoxPH.r$logtest,      summaryCoxPH.h2o@summary$logtest,
              tolerance = tolerance)
  checkEquals(summaryCoxPH.r$sctest,       summaryCoxPH.h2o@summary$sctest,
              tolerance = tolerance)
  checkEquals(summaryCoxPH.r$rsq,          summaryCoxPH.h2o@summary$rsq,
              tolerance = tolerance)
  checkEquals(summaryCoxPH.r$waldtest,
              c(round(summaryCoxPH.h2o@summary$waldtest[1L], 2),
                summaryCoxPH.h2o@summary$waldtest[2:3]),
              tolerance = tolerance)
  
  for (args in list(list(conf.int = 0.90, conf.type = "log"),
                    list(conf.int = 0.95, conf.type = "log-log"),
                    list(conf.int = 0.99, conf.type = "plain"),
                    list(conf.int = 0.00, conf.type = "none"))) {
    conf.int  <- args$conf.int
    conf.type <- args$conf.type
    survfitCoxPH.h2o <- survfit(myCoxPH.h2o, conf.int = conf.int, conf.type = conf.type)
    survfitCoxPH.r   <- survfit(myCoxPH.r,   conf.int = conf.int, conf.type = conf.type)
    cat("\nH2O Cox Proportional Hazards Model Survival Fit: baseline hazard, ",
        "conf.int = ", conf.int, ", conf.type = \"", conf.type, "\"\n", sep = "")
    print(survfitCoxPH.h2o)
    cat("\nsurvival Package Cox Proportional Hazards Model Survival Fit: baseline hazard, ",
        "conf.int = ", conf.int, ", conf.type = \"", conf.type, "\"\n", sep = "")
    print(survfitCoxPH.r)
    checkCoxPHSurvfit(survfitCoxPH.h2o, survfitCoxPH.r, tolerance = tolerance)
  }
  survfitCoxPH.h2o <- survfit(myCoxPH.h2o, newdata = myCoxPH.h2o@data)
  survfitCoxPH.r   <- survfit(myCoxPH.r, newdata = eval(myCoxPH.r$call$data, sys.parent()))
  checkCoxPHSurvfit(survfitCoxPH.h2o, survfitCoxPH.r, tolerance = tolerance)
  
  checkEquals(coef(myCoxPH.r),       coef(myCoxPH.h2o),
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(coef(summaryCoxPH.r),  coef(summaryCoxPH.h2o),
              tolerance = tolerance, check.attributes = FALSE)
  checkEquals(extractAIC(myCoxPH.r), extractAIC(myCoxPH.h2o),
              tolerance = tolerance)
  checkEquals(extractAIC(myCoxPH.r,   k = log(myCoxPH.r$n)),
              extractAIC(myCoxPH.h2o, k = log(myCoxPH.r$n)),
              tolerance = tolerance)
  checkEquals(logLik(myCoxPH.r),     logLik(myCoxPH.h2o),
              tolerance = tolerance)
  checkEquals(vcov(myCoxPH.r),       vcov(myCoxPH.h2o),
              tolerance = tolerance, check.attributes = FALSE)
  
  invisible(TRUE)
}

checkCoxPHSurvfit <- function(survfitCoxPH.h2o, survfitCoxPH.r, tolerance = 1e-8, ...) {
  checkEquals(survfitCoxPH.r$n,         survfitCoxPH.h2o$n)
  checkEquals(survfitCoxPH.r$time,      survfitCoxPH.h2o$time)
  checkEquals(survfitCoxPH.r$n.risk,    survfitCoxPH.h2o$n.risk)
  checkEquals(survfitCoxPH.r$n.event,   survfitCoxPH.h2o$n.event)
  checkEquals(survfitCoxPH.r$n.censor,  survfitCoxPH.h2o$n.censor)
  if (is.matrix(survfitCoxPH.h2o$surv)) {
    ok <- !is.na(tail(survfitCoxPH.r$surv, 1L))
    checkEquals(survfitCoxPH.r$surv[, c(ok)],
                survfitCoxPH.h2o$surv[, c(ok)],
                tolerance = sqrt(tolerance),
                check.attributes = FALSE)
  } else if (!anyNA(survfitCoxPH.r$surv)) {
    checkEquals(survfitCoxPH.r$surv,
                survfitCoxPH.h2o$surv,
                tolerance = sqrt(tolerance))
  }
  checkEquals(survfitCoxPH.r$type,      survfitCoxPH.h2o$type)
  checkEquals(survfitCoxPH.r$cumhaz,    survfitCoxPH.h2o$cumhaz,
              tolerance = sqrt(tolerance))
  #checkEquals(survfitCoxPH.r$std.err,   survfitCoxPH.h2o$std.err,
  #            tolerance = sqrt(tolerance))
  #checkEquals(survfitCoxPH.r$upper,     survfitCoxPH.h2o$upper,
  #            tolerance = sqrt(tolerance),
  #            check.attributes = FALSE)
  #checkEquals(survfitCoxPH.r$lower,     survfitCoxPH.h2o$lower,
  #            tolerance = sqrt(tolerance),
  #            check.attributes = FALSE)
  #checkEquals(survfitCoxPH.r$conf.type, survfitCoxPH.h2o$conf.type)
  #checkEquals(survfitCoxPH.r$conf.int,  survfitCoxPH.h2o$conf.int)
  
  invisible(TRUE)
}
