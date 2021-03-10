setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.CoxPH.pbc_offset <- function() {
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
    
  pbc1 <- transform(pbc,
                    status2 = status == 2,
                    log_bili = log(bili),
                    log_albumin = log(albumin),
                    protime_offset = 2.4 * log(protime))
  pbc1$protime_offset[is.na(pbc1$protime_offset)] <- mean(pbc1$protime_offset, na.rm = TRUE)

  fit <- coxph(Surv(time, status2) ~ age + edema + log_bili + log_albumin + offset(protime_offset), data = pbc1)
  print(fit)
  print(summary(fit))

  hex.fit.1 <- h2o.coxph(x = c("age", "edema", "log_bili", "log_albumin"), offset_column = "protime_offset",
                         event_column = "status2", stop_column = "time", ties = "efron",
                         training_frame = as.h2o(pbc1))
  print(hex.fit.1)
  print(summary(hex.fit.1))

  coef.r <- fit$coefficients
  coef.hex.1 <- .as.survival.coxph.model(hex.fit.1@model)$coef[names(coef.r)] # reorder
  expect_equal(coef.r, coef.hex.1)

  expect_equal(coef(summary(fit)), coef(summary(hex.fit.1)))
    
  check.pred(fit, hex.fit.1, pbc1, as.h2o(pbc1))
}

doTest("CoxPH: Offset Test", test.CoxPH.pbc_offset)
