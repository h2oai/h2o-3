setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.rf.colsamplepertree <- function() {
  covtype <- h2o.importFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])
  splits <- h2o.splitFrame(covtype, 0.8, seed=1234)
  train <- splits[[1]]
  valid <- splits[[2]]

  regular      <- h2o.randomForest(x=1:54,y=55,ntrees=50,seed=1234,training_frame=train)
  colsample    <- h2o.randomForest(x=1:54,y=55,ntrees=50,seed=1234,training_frame=train,col_sample_rate_per_tree=0.9)

  mm_regular   <- h2o.performance(regular, valid)
  mm_colsample <- h2o.performance(colsample, valid)

  err_regular   <- h2o.confusionMatrix(mm_regular)[8,8]
  err_colsample <- h2o.confusionMatrix(mm_colsample)[8,8]


  print("err_regular")
  print(err_regular)
  print("")
  print("err_colsample")
  print(err_colsample)

  expect_true(err_regular >= 0.9*err_colsample, "col sampling made validation error worse!")
}

h2oTest.doTest("rf colSamplePerTree", test.rf.colsamplepertree)
