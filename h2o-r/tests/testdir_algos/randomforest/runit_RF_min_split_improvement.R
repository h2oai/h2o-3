setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.rf.min_split_improvement<- function() {
  covtype <- h2o.importFile(locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])
  train <- covtype[1:floor(nrow(covtype)/2),]
  valid <- covtype[(floor(nrow(covtype)/2)+1):nrow(covtype),]

  regular                  <- h2o.randomForest(x=1:54,y=55,ntrees=20,max_depth=15,seed=1234,training_frame=train)
  min_split_improvement    <- h2o.randomForest(x=1:54,y=55,ntrees=20,max_depth=15,seed=1234,training_frame=train,min_split_improvement=1e-4)

  mm_regular   <- h2o.performance(regular, valid)
  mm_min_split_improvement <- h2o.performance(min_split_improvement, valid)

  err_regular   <- h2o.logloss(mm_regular)
  err_min_split_improvement <- h2o.logloss(mm_min_split_improvement)


  print("err_regular")
  print(err_regular)
  print("")
  print("err_min_split_improvement")
  print(err_min_split_improvement)

  expect_true(err_regular >= err_min_split_improvement, "min split improvement made validation logloss error worse!")
}

doTest("rf minSplitImprovement", test.rf.min_split_improvement)
