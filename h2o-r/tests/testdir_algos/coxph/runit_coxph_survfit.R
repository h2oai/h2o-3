setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.CoxPH.survfit_ovarian <- function() {
  fit <- coxph(Surv(futime, fustat) ~ age + rx + ecog.ps, data = ovarian)
  print(fit)
  print(survfit(fit))
  
  hex.fit.1 <- h2o.coxph(x = c("age", "rx", "ecog.ps"),
                         event_column = "fustat", stop_column = "futime", ties = "efron",
                         training_frame = as.h2o(ovarian))
  print(hex.fit.1)
  print(survfit(hex.fit.1))
  
  coef.r <- coef(fit)
  coef.hex.1 <- coef(hex.fit.1)[names(coef.r)] # reorder
  expect_equal(coef.r, coef.hex.1)

  unsupported <- "logse" # logse is not yet supported

  surv.r <- unclass(survfit(fit, se.fit = FALSE))
  surv.r$call <- NULL
  surv.r[unsupported] <- NULL
  surv.hex.1 <- unclass(survfit(hex.fit.1))[setdiff(names(surv.r), unsupported)]
  surv.hex.1[unsupported] <- NULL
  expect_equal(surv.r, surv.hex.1)
}

doTest("CoxPH: Survfit Test", test.CoxPH.survfit_ovarian)
