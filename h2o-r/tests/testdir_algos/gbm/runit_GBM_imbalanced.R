setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.gbm.imbalanced <- function() {
  covtype <- h2o.uploadFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])

  hh_imbalanced<-h2o.gbm(x=c(1:54),y=55,ntrees=10,min_rows=5,learn_rate=0.2,training_frame=covtype,distribution="multinomial",balance_classes=F)
  hh_balanced  <-h2o.gbm(x=c(1:54),y=55,ntrees=10,min_rows=5,learn_rate=0.2,training_frame=covtype,distribution="multinomial",balance_classes=T, seed=1) #seed is used for (over-)sampling to obtain class balance
  hh_imbalanced_metrics <- h2o.performance(hh_imbalanced)
  hh_balanced_metrics   <- h2o.performance(hh_balanced  )

  #compare error for class 6 (difficult minority)
  class_6_err_imbalanced <- h2o.confusionMatrix(hh_imbalanced_metrics)[6,8]
  class_6_err_balanced   <- h2o.confusionMatrix(hh_balanced_metrics)[6,8]

  print("class_6_err_imbalanced")
  print(class_6_err_imbalanced)
  print("")
  print("class_6_err_balanced")
  print(class_6_err_balanced)

  expect_true(class_6_err_imbalanced >= 0.99*class_6_err_balanced, "balance_classes makes it at least 1% worse!")

  
}

h2oTest.doTest("GBM imbalanced", test.gbm.imbalanced)
