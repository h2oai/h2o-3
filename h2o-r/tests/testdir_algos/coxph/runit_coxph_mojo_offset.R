setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.CoxPH.mojo_offset <- function() {
  pbc1 <- transform(survival::pbc,
                    status2 = status == 2,
                    log_bili = log(bili),
                    log_albumin = log(albumin),
                    protime_offset = 2.4 * log(protime))
  pbc1$protime_offset[is.na(pbc1$protime_offset)] <- mean(pbc1$protime_offset, na.rm = TRUE)

  pbc1.h2o <- as.h2o(pbc1)
  hex.fit.1 <- h2o.coxph(x = c("age", "edema", "log_bili", "log_albumin"), offset_column = "protime_offset",
                         event_column = "status2", stop_column = "time", ties = "efron",
                         training_frame = pbc1.h2o)
  predicted <- h2o.predict(hex.fit.1, pbc1.h2o)

  assertTestJavaScoring(hex.fit.1, pbc1.h2o, predicted, 1e-8)
}

doTest("CoxPH: MOJO Offset Test", test.CoxPH.mojo_offset)
