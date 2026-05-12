setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.relevel <- function() {
  #compare against itself
  Log.info("Importing prostate_cat.csv data...\n")
  d <- h2o.importFile(locate("smalldata/prostate/prostate_cat.csv"),na.strings=rep("NA", 8))

  myY <- 1
  myX <- 2:ncol(d)
  mh2o1 <- h2o.glm(training_frame=d, x=myX, y=myY, family="binomial", lambda=0, missing_values_handling = "Skip")
  ns <- names(mh2o1@model$coefficients)

  print(mh2o1@model$coefficients)

  expect_true("DPROS.None" %in% ns, "None level IS NOT expected to be skipped by default")
  expect_true(!("DPROS.Both" %in% ns), "Both level IS expected to be skipped by default")

  x <- h2o.relevel(d$DPROS,"None")
  print(x)

  d$DPROS <- x[,1]
  mh2o2 <- h2o.glm(training_frame=d, x=myX, y=myY, family="binomial", lambda=0, missing_values_handling = "Skip")
  print(mh2o2@model$coefficients)

  ns2 <- names(mh2o2@model$coefficients)
  expect_true(!("DPROS.None" %in% ns2), "None level IS expected to be skipped in re-leveled column")
  expect_true(("DPROS.Both" %in% ns2), "Both level IS NOT expected to be skipped in re-leveled column")

  # compare against R
  dr <- read.csv(locate("smalldata/prostate/prostate_cat.csv"), stringsAsFactors=TRUE)
  dr$DPROS <- relevel(dr$DPROS,"None")
  mr <- glm(data=dr,CAPSULE ~ ., family=binomial)
  print(mr)

  # result from R but manualy reordered and renamed to match h2o naming and order
  exp_coefs <- c(-7.63245,  1.39185, 0.73482, 1.51437, 0.65160,0.49233, -0.01189, 0.02990, -0.01141, 0.96466927)
  names(exp_coefs) <- c("Intercept", "DPROS.Both", "DPROS.Left", "DPROS.Right", "RACE.White", "DCAPS.Yes", "AGE", "PSA", "VOL", "GLEASON")
  expect_true(max(abs(exp_coefs - mh2o2@model$coefficients)) < 1e-4)
}

doTest("Test h2o.relevel call on prostate", test.relevel)

