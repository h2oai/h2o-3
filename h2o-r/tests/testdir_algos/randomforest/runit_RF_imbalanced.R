setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.rf.imbalanced <- function(conn) {
  covtype <- h2o.uploadFile(conn, locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])

  hh_imbalanced<-h2o.randomForest(x=1:54,y=55,ntrees=50,training_frame=covtype, balance_classes=F)
  hh_balanced  <-h2o.randomForest(x=1:54,y=55,ntrees=50,training_frame=covtype, balance_classes=T)
  hh_imbalanced_metrics <- h2o.performance(hh_imbalanced)
  hh_balanced_metrics   <- h2o.performance(hh_balanced  )

  class_6_err_imbalanced <- h2o.logloss(hh_imbalanced)
  class_6_err_balanced <- h2o.logloss(hh_balanced)

  print("class_6_err_imbalanced")
  print(class_6_err_imbalanced)
  print("")
  print("class_6_err_balanced")
  print(class_6_err_balanced)

  expect_true(class_6_err_imbalanced >= class_6_err_balanced, "balance_classes makes it worse!")

  testEnd()
}

doTest("rf imbalanced", test.rf.imbalanced)
