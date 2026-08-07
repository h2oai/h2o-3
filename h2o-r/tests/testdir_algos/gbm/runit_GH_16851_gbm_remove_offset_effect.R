setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Flag-ON client test for GH-16851: exercises the full user path through the R client —
# setting remove_offset_effects, offset-free predictions, the dual "unrestricted" metric view in the
# model output, and scoring a frame without the offset column.

test.gbm.remove_offset.effect <- function() {
  df <- read.csv(locate("smalldata/prostate/prostate.csv"))
  df$offset <- cos(df$ID) * 0.3
  hf <- as.h2o(df)
  x <- c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  ro <- h2o.gbm(x=x, y="AGE", training_frame=hf, offset_column="offset",
                distribution="gaussian", ntrees=20, max_depth=4, seed=42,
                remove_offset_effects=TRUE)
  plain <- h2o.gbm(x=x, y="AGE", training_frame=hf, offset_column="offset",
                   distribution="gaussian", ntrees=20, max_depth=4, seed=42)

  hz <- hf
  hz$offset <- hz$offset * 0

  roPreds     <- as.data.frame(h2o.predict(ro, hf))[, 1]
  roZeroed    <- as.data.frame(h2o.predict(ro, hz))[, 1]
  plainZeroed <- as.data.frame(h2o.predict(plain, hz))[, 1]
  plainPreds  <- as.data.frame(h2o.predict(plain, hf))[, 1]

  # predictions ignore the offset column and equal the identically-fit plain model on zero offset
  expect_equal(roPreds, roZeroed, tolerance=0)
  expect_equal(roPreds, plainZeroed, tolerance=1e-8)
  expect_true(max(abs(plainPreds - roPreds)) > 1e-6)

  # dual view is visible in the model output (ModelOutputSchemaV3 exposure); depending on the client
  # version the field materializes as an H2OModelMetrics object or a plain list
  unrestricted <- ro@model$training_metrics_unrestricted_model
  expect_false(is.null(unrestricted))
  unrestrictedMse <- if (isS4(unrestricted)) unrestricted@metrics$MSE else unrestricted$MSE
  restrictedMse <- ro@model$training_metrics@metrics$MSE
  expect_true(abs(unrestrictedMse - restrictedMse) > 1e-6)

  # scoring a frame WITHOUT the offset column works (zero column substituted)
  noOffset <- as.h2o(df[, setdiff(names(df), "offset")])
  noOffsetPreds <- as.data.frame(h2o.predict(ro, noOffset))[, 1]
  expect_equal(roPreds, noOffsetPreds, tolerance=0)
}

doTest("GBM remove_offset_effects via the R client (predictions, dual view, offset-less scoring)", test.gbm.remove_offset.effect)
