setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained model that does NOT use remove_offset_effects.
# Unlike GLM/GBM, DeepLearning applies the offset in standardized/response space rather than as a clean
# link-scale add, so we pin only that scoring is deterministic and that the offset is applied (predictions
# differ from the offset-zeroed frame). Offset is supported for regression only in DL (no classification).

test.deeplearning.remove_offset.baseline <- function() {
  df <- read.csv(locate("smalldata/prostate/prostate.csv"))
  df$offset <- cos(df$ID) * 0.3                 # deterministic, row-aligned, small
  x <- c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  configs <- list(
    list(family="gaussian", y="AGE", col=1),
    list(family="poisson",  y="AGE", col=1),
    list(family="gamma",    y="AGE", col=1),
    list(family="tweedie",  y="AGE", col=1)
  )

  for (cfg in configs) {
    hf <- as.h2o(df)
    args <- list(x=x, y=cfg$y, training_frame=hf, offset_column="offset",
                 distribution=cfg$family, hidden=c(8, 8), epochs=30, reproducible=TRUE, seed=42)
    if (cfg$family == "tweedie") args$tweedie_power <- 1.5
    m <- do.call(h2o.deeplearning, args)

    predsA  <- as.data.frame(h2o.predict(m, hf))[, cfg$col]
    predsA2 <- as.data.frame(h2o.predict(m, hf))[, cfg$col]
    expect_equal(predsA, predsA2, tolerance=0)          # default scoring is deterministic

    hz <- hf
    hz$offset <- hz$offset * 0
    predsZero <- as.data.frame(h2o.predict(m, hz))[, cfg$col]

    expect_true(max(abs(predsA - predsZero)) > 1e-6)    # default scoring must apply the offset
  }
}

doTest("DeepLearning remove_offset baseline (offset applied by default, regression families)", test.deeplearning.remove_offset.baseline)
