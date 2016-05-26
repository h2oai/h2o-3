setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.pubdev.2118 <- function(conn){
  df <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
  df$CAPSULE <- as.factor(df$CAPSULE)
  m <- h2o.gbm(1:ncol(df),"CAPSULE",df, validation_frame=df)
  expected<-c(2.4836601,2.4836601,2.4836601,2.4836601,2.4836601,2.4836601,2.4836601,2.4836601,2.3529412,1.8954248,0.6535948,0.1307190,0.0000000,0.0000000,0.0000000,0.0000000)

  t <- h2o.gainsLift(m)
  print(t$lift)
  expect_true(max(abs(t$lift-expected))<1e-6)

  t <- h2o.gainsLift(m, valid=T)
  expect_true(max(abs(t$lift-expected))<1e-6)

  t <- h2o.gainsLift(m,df)
  expect_true(max(abs(t$lift-expected))<1e-6)

  m <- h2o.gbm(1:ncol(df),"CAPSULE",df, validation_frame=df, nfolds=3, seed=1234)
  t <- h2o.gainsLift(m,xval=T)
  expect_true(abs(t$cumulative_lift[5] - 1.960784) < 1e-5) ## lift in top group
}

doTest("PUBDEV-2118", test.pubdev.2118)
