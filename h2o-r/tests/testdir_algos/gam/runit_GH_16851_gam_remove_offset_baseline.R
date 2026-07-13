setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained GAM that does NOT use remove_offset_effects.
# GAM fits through GLM, so the offset enters at the link scale and the oracle is identical to GLM/GBM:
#   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly.

g <- function(p, link) {
  if (link == "log")   return(log(p))
  if (link == "logit") return(log(p / (1 - p)))
  p
}

test.gam.remove_offset.baseline <- function() {
  df <- read.csv(locate("smalldata/prostate/prostate.csv"))
  df$offset <- cos(df$ID) * 0.3                 # deterministic, row-aligned, small (keeps exp() sane)
  # Linear predictors; PSA is the smooth term (gam_columns) and is NOT included in x.
  x <- c("RACE", "DPROS", "DCAPS", "VOL", "GLEASON")

  configs <- list(
    list(family="gaussian",  y="AGE",     link="identity", col=1),
    list(family="poisson",   y="AGE",     link="log",      col=1),
    # gamma omitted: with lambda=0 + a spline term the IRLSM line search fails to converge (NaN);
    # gamma+offset is already covered by the GLM/GBM/XGBoost baselines.
    list(family="tweedie",   y="AGE",     link="log",      col=1),
    list(family="binomial",  y="CAPSULE", link="logit",    col=3)  # positive-class prob is col 3
  )

  for (cfg in configs) {
    d <- df
    if (cfg$link == "logit") d$CAPSULE <- as.factor(d$CAPSULE)
    hf <- as.h2o(d)
    args <- list(x=x, y=cfg$y, training_frame=hf, offset_column="offset",
                 family=cfg$family, gam_columns=c("PSA"), num_knots=c(5), lambda=0, solver="irlsm")
    if (cfg$family == "tweedie") {
      args$tweedie_variance_power <- 1.5
      args$tweedie_link_power <- 0
    }
    m <- do.call(h2o.gam, args)

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

doTest("GAM remove_offset baseline (offset applied by default, all families)", test.gam.remove_offset.baseline)
