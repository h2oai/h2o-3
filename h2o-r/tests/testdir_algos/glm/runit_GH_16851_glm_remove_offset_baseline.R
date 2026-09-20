setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained model that does NOT use remove_offset_effects.
# Oracle (no frozen values): the offset always enters at the link scale, so for every family
#   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly.
# GLM selects behavior via family (not distribution); lambda=0 disables regularization so the fit is a
# plain GLM. gamma defaults to the inverse link so it is forced to log; tweedie keeps its tweedie link
# with tweedie_link_power=0 (= log scale).

g <- function(p, link) {
  if (link == "log")   return(log(p))
  if (link == "logit") return(log(p / (1 - p)))
  p
}

test.glm.remove_offset.baseline <- function() {
  df <- read.csv(locate("smalldata/prostate/prostate.csv"))
  df$offset <- cos(df$ID) * 0.3                 # deterministic, row-aligned, small (keeps exp() sane)
  x <- c("RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")

  configs <- list(
    list(family="gaussian", y="AGE",     link="identity", col=1, glm_link=NULL),
    list(family="poisson",  y="AGE",     link="log",      col=1, glm_link=NULL),
    list(family="gamma",    y="AGE",     link="log",      col=1, glm_link="log"),  # gamma defaults to inverse link
    list(family="tweedie",  y="AGE",     link="log",      col=1, glm_link=NULL),
    list(family="binomial", y="CAPSULE", link="logit",    col=3, glm_link=NULL)    # positive-class prob is col 3
  )

  for (cfg in configs) {
    d <- df
    if (cfg$link == "logit") d$CAPSULE <- as.factor(d$CAPSULE)
    hf <- as.h2o(d)
    args <- list(x=x, y=cfg$y, training_frame=hf, offset_column="offset",
                 family=cfg$family, lambda=0)
    if (!is.null(cfg$glm_link)) args$link <- cfg$glm_link
    if (cfg$family == "tweedie") {
      args$tweedie_variance_power <- 1.5
      args$tweedie_link_power <- 0                       # 0 -> log link
    }
    m <- do.call(h2o.glm, args)

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

doTest("GLM remove_offset baseline (offset applied by default, all families)", test.glm.remove_offset.baseline)
