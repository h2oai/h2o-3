setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained CoxPH model that does NOT use
# remove_offset_effects. CoxPH's prediction is a clean linear predictor ("lp") and the offset
# enters the linear predictor with coefficient 1.0, so the exact oracle holds:
#   lp(withOffset) - lp(offsetZeroed) == offset.
# We also assert default scoring is deterministic and that the offset genuinely moves predictions.

test.coxph.remove_offset.baseline <- function() {
  df <- read.csv(locate("smalldata/coxph_test/heart.csv"))
  df$offset <- cos(df$id) * 0.3                 # deterministic, row-aligned, small
  hf <- as.h2o(df)

  m <- h2o.coxph(x="age", event_column="event", start_column="start", stop_column="stop",
                 offset_column="offset", ties="efron", training_frame=hf)

  predsA  <- as.data.frame(h2o.predict(m, hf))$lp
  predsA2 <- as.data.frame(h2o.predict(m, hf))$lp
  expect_equal(predsA, predsA2, tolerance=0)          # default scoring is deterministic

  hz <- hf
  hz$offset <- hz$offset * 0
  predsZero <- as.data.frame(h2o.predict(m, hz))$lp

  expect_true(max(abs(predsA - predsZero)) > 1e-6)    # default scoring must apply the offset
  err <- max(abs((predsA - predsZero) - df$offset))
  expect_true(err < 1e-6, info=paste("lp(withOffset) - lp(offsetZeroed) must equal offset, err=", err))
}

doTest("CoxPH remove_offset baseline (offset applied by default)", test.coxph.remove_offset.baseline)
