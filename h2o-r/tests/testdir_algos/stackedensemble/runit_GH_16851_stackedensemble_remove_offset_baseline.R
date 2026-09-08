setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained StackedEnsemble that does NOT use
# remove_offset_effects. StackedEnsemble has no single-family link oracle (base models + metalearner),
# so we use the simpler baseline oracle: default scoring is deterministic and the offset genuinely
# changes predictions.

test.stackedensemble.remove_offset.baseline <- function() {
  df <- read.csv(locate("smalldata/prostate/prostate.csv"))
  df$offset <- cos(df$ID) * 0.3                 # deterministic, row-aligned, small
  x <- c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  configs <- list(
    list(family="gaussian",  y="AGE",     col=1),
    list(family="bernoulli", y="CAPSULE", col=3)  # positive-class prob is col 3
  )

  for (cfg in configs) {
    d <- df
    if (cfg$family == "bernoulli") d$CAPSULE <- as.factor(d$CAPSULE)
    hf <- as.h2o(d)

    base <- list()
    for (seed in c(42, 7)) {
      g <- h2o.gbm(x=x, y=cfg$y, training_frame=hf, offset_column="offset",
                   distribution=cfg$family, ntrees=10, max_depth=3, seed=seed,
                   nfolds=3, fold_assignment="Modulo", keep_cross_validation_predictions=TRUE)
      base <- c(base, g@model_id)
    }

    se <- h2o.stackedEnsemble(x=x, y=cfg$y, training_frame=hf, base_models=base,
                              offset_column="offset", seed=42)

    predsA  <- as.data.frame(h2o.predict(se, hf))[, cfg$col]
    predsA2 <- as.data.frame(h2o.predict(se, hf))[, cfg$col]
    expect_equal(predsA, predsA2, tolerance=0)          # default scoring is deterministic

    hz <- hf
    hz$offset <- hz$offset * 0
    predsZero <- as.data.frame(h2o.predict(se, hz))[, cfg$col]

    expect_true(max(abs(predsA - predsZero)) > 1e-6)    # default scoring must apply the offset
  }
}

doTest("StackedEnsemble remove_offset baseline (offset applied by default)", test.stackedensemble.remove_offset.baseline)
