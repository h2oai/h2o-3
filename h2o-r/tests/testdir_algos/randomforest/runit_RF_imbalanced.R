setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.rf.imbalanced <- function() {
  covtype <- h2o.uploadFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])

  hh_imbalanced<-h2o.randomForest(x=1:54,y=55,ntrees=50,training_frame=covtype, balance_classes=F)
  hh_balanced  <-h2o.randomForest(x=1:54,y=55,ntrees=50,training_frame=covtype, balance_classes=T)
  hh_imbalanced_metrics <- h2o.performance(hh_imbalanced)
  hh_balanced_metrics   <- h2o.performance(hh_balanced  )

  #compare error for class 6 (difficult minority)
  #confusion_matrix element at position A,P for N classes is at: model$confusion[P*(N+1)-(N-A+1)]
  #Here, A=6 P=8, N=7 -> need element 8*(7+1)-(7-6+1) = 62

  class_6_err_imbalanced <- h2o.confusionMatrix(hh_imbalanced_metrics)[6,8]
  class_6_err_balanced   <- h2o.confusionMatrix(hh_balanced_metrics)[6,8]


  print("class_6_err_imbalanced")
  print(class_6_err_imbalanced)
  print("")
  print("class_6_err_balanced")
  print(class_6_err_balanced)

  expect_true(class_6_err_imbalanced >= 0.9*class_6_err_balanced, "balance_classes makes it at least 10% worse!")

  
}

h2oTest.doTest("rf imbalanced", test.rf.imbalanced)
