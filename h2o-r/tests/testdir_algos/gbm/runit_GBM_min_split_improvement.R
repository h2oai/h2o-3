setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.gbm.min_split_improvement<- function() {
  covtype <- h2o.importFile(locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])
  train <- covtype[1:floor(nrow(covtype)/2),]
  valid <- covtype[(floor(nrow(covtype)/2)+1):nrow(covtype),]

  regular                  <- h2o.gbm(x=1:54,y=55,ntrees=10,learn_rate=0.05,max_depth=5,training_frame=train,validation_frame=valid)
  min_split_improvement    <- h2o.gbm(x=1:54,y=55,ntrees=10,learn_rate=0.05,max_depth=5,training_frame=train,validation_frame=valid,min_split_improvement=1e-1)
  err_regular   <- h2o.logloss(regular,valid=TRUE)
  err_min_split_improvement <- h2o.logloss(min_split_improvement,valid=TRUE)

  #mm_regular   <- h2o.performance(regular, valid)
  #mm_min_split_improvement <- h2o.performance(min_split_improvement, valid)

  #err_regular   <- h2o.logloss(mm_regular)
  #err_min_split_improvement <- h2o.logloss(mm_min_split_improvement)


  print("err_regular")
  print(err_regular)
  print("")
  print("err_min_split_improvement")
  print(err_min_split_improvement)

  expect_true(err_regular < err_min_split_improvement, "large value of min split improvement should have made validation logloss error worse!")
}

doTest("gbm minSplitImprovement", test.gbm.min_split_improvement)
