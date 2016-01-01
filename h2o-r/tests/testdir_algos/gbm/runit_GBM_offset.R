setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM <- function() {
  library(gbm)
  df <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"), destination_frame="prostate.hex")

  ## AGE Regression
  glm <- h2o.glm(x=4:8,y="AGE",training_frame=df)
  df$offset <- h2o.predict(glm, df)
  m1 <- h2o.gbm(x=4:8,y="AGE",training_frame=df)
  model <- gbm(AGE~.-AGE-ID, data=as.data.frame(df), distribution="gaussian")
  expect_true(abs((m1@model$init_f - model$initF)/model$initF) < 1e-6, "initF mismatch without offset for gaussian")

  m2 <- h2o.gbm(x=4:8,y="AGE",training_frame=df, offset_column="offset")
  expect_true(abs((h2o.mse(m1) - h2o.mse(m2))/h2o.mse(m1)) < 5e-2, "MSE with and without offset are too different for gaussian")

  model <- gbm(AGE~.-AGE-ID-CAPSULE + offset(offset), data=as.data.frame(df), distribution="gaussian")
  expect_true(abs((m2@model$init_f - model$initF)/model$initF) < 5e-2, "initF mismatch with offset for gaussian")



  ## CAPSULE Binary Classification (GBM Bernoulli)
  glm <- h2o.glm(x=3:8,y="CAPSULE",training_frame=df,family="binomial")
  df$offset <- h2o.predict(glm, df)[,3]
  df$CAPSULE <- as.factor(df$CAPSULE)

  gbm <- h2o.gbm(x=3:8,y="CAPSULE",training_frame=df)
  ldf <- as.data.frame(df)
  ldf$CAPSULE <- as.integer(ldf$CAPSULE) - 1
  model <- gbm(CAPSULE~.-CAPSULE-ID, data=ldf, distribution="bernoulli")

  expect_true(h2o.mse(gbm) < 0.11, "MSE too big without offset")
  expect_true(abs((gbm@model$init_f - model$initF)/model$initF) < 1e-6, "initF mismatch without offset for bernoulli")

  # Now add offset
  gbm <- h2o.gbm(x=3:8,y="CAPSULE",training_frame=df, offset_column="offset", distribution="bernoulli")
  expect_true(h2o.mse(gbm) < 0.11, "MSE too big with offset for bernoulli")

  model <- gbm(CAPSULE~.-CAPSULE-ID + offset(offset), data=ldf, distribution="bernoulli")
  expect_true(abs((gbm@model$init_f - model$initF)/model$initF) < 1e-6, "initF mismatch with offset for bernoulli")

  
}
h2oTest.doTest("GBM Test: offset", test.GBM)
