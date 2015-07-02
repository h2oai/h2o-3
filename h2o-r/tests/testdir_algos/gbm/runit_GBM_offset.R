setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM <- function(conn) {
  library(gbm)
  df <- h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv"), destination_frame="prostate.hex")

  ## AGE Regression
  glm <- h2o.glm(x=4:8,y="AGE",training_frame=df)
  df$offset <- h2o.predict(glm, df)
  m1 <- h2o.gbm(x=4:8,y="AGE",training_frame=df)
  m2 <- h2o.gbm(x=4:8,y="AGE",training_frame=df, offset_column="offset")
  expect_true(abs((h2o.mse(m1) - h2o.mse(m2))/h2o.mse(m1)) < 5e-2, "MSE with and without offset are too different")


  ## CAPSULE Binary Classification
  glm <- h2o.glm(x=3:8,y="CAPSULE",training_frame=df,family="binomial")
  df$offset <- h2o.predict(glm, df)[,3]
  df$CAPSULE <- as.factor(df$CAPSULE)

  gbm <- h2o.gbm(x=3:8,y="CAPSULE",training_frame=df)
  gbm@model$init_f
  model <- gbm(CAPSULE~.-CAPSULE-ID, data=as.data.frame(df), distribution="bernoulli")
  model
  expect_true(h2o.mse(gbm) < 0.11, "MSE too big without offset")
  model$initF
  expect_true(abs((gbm@model$init_f - model$initF)/model$initF) < 1e-6, "initF mismatch without offset")

  # Now add offset
  gbm <- h2o.gbm(x=3:8,y="CAPSULE",training_frame=df, offset_column="offset", distribution="bernoulli")
  gbm
  gbm@model$init_f
  expect_true(h2o.mse(gbm) < 0.11, "MSE too big with offset")

  model <- gbm(CAPSULE~.-CAPSULE-ID + offset(offset), data=as.data.frame(df), distribution="bernoulli")
  model
  model$initF

  expect_true(abs((gbm@model$init_f - model$initF)/model$initF) < 0.03, "initF mismatch with offset")

  testEnd()
}
doTest("GBM Test: offset", test.GBM)
