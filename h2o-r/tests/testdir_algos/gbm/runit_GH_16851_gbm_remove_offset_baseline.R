setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained model that does NOT use remove_offset_effects.
# Oracle (no frozen values): the offset always enters at the link scale, so for every family
#   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly.

g <- function(p, link) {
  if (link == "log")   return(log(p))
  if (link == "logit") return(log(p / (1 - p)))
  p
}

test.gbm.remove_offset.baseline <- function() {
  df <- read.csv(locate("smalldata/prostate/prostate.csv"))
  df$offset <- cos(df$ID) * 0.3                 # deterministic, row-aligned, small (keeps exp() sane)
  x <- c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  configs <- list(
    list(family="gaussian",  y="AGE",     link="identity", col=1),
    list(family="poisson",   y="AGE",     link="log",      col=1),
    list(family="gamma",     y="AGE",     link="log",      col=1),
    list(family="tweedie",   y="AGE",     link="log",      col=1),
    list(family="bernoulli", y="CAPSULE", link="logit",    col=3)  # positive-class prob is col 3
  )

  for (cfg in configs) {
    d <- df
    if (cfg$link == "logit") d$CAPSULE <- as.factor(d$CAPSULE)
    hf <- as.h2o(d)
    args <- list(x=x, y=cfg$y, training_frame=hf, offset_column="offset",
                 distribution=cfg$family, ntrees=20, max_depth=4, seed=42)
    if (cfg$family == "tweedie") args$tweedie_power <- 1.5
    m <- do.call(h2o.gbm, args)

    predsA  <- as.data.frame(h2o.predict(m, hf))[, cfg$col]
    predsA2 <- as.data.frame(h2o.predict(m, hf))[, cfg$col]
    expect_equal(predsA, predsA2, tolerance=0)          # default scoring is deterministic

    hz <- hf
    hz$offset <- hz$offset * 0
    predsZero <- as.data.frame(h2o.predict(m, hz))[, cfg$col]

    expect_true(max(abs(predsA - predsZero)) > 1e-6)    # default scoring must apply the offset
    err <- max(abs((g(predsA, cfg$link) - g(predsZero, cfg$link)) - d$offset))
    expect_true(err < 1e-6, info=paste(cfg$family, "link(predWith)-link(predZero) must equal offset, err=", err))
  }
}

doTest("GBM remove_offset baseline (offset applied by default, all families)", test.gbm.remove_offset.baseline)
