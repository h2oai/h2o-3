setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.pubdev.2118 <- function(conn){
  df <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  df$CAPSULE <- as.factor(df$CAPSULE)
  m <- h2o.gbm(1:ncol(df),"CAPSULE",df, validation_frame=df)
  expected<-c(2.48366013,2.48366013,2.48366013,2.48366013,2.48366013,2.48366013,2.48366013,2.48366013,2.41830065,1.69934641,0.84967320,0.06535948,0.00000000,0.00000000,0.00000000,0.00000000)

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

h2oTest.doTest("PUBDEV-2118", test.pubdev.2118)
