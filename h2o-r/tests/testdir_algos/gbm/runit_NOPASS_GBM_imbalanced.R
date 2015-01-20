setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gbm.imbalanced <- function(conn) {
  covtype <- h2o.uploadFile(conn, locate("smalldata/covtype/covtype.20k.data"))

  hh_imbalanced<-h2o.gbm.cv(x=c(1:54),y=55,ntrees=50,training_frame=covtype,do_classification=T,balance_classes=F, nfolds=10)
  hh_imbalanced_metrics <- h2o.performance(hh_imbalanced)
  print(hh_imbalanced_metrics)
  hh_balanced<-h2o.gbm.cv(x=c(1:54),y=55,ntrees=50,training_frame=covtype,do_classification=T,balance_classes=T,nfolds=10)
  hh_balanced_metrics <- h2o.performance(hh_balanced)
  # print(hh_balanced_metrics)

  #compare error for class 6 (difficult minority)
  #confusion_matrix element at position A,P for N classes is at: model$confusion[P*(N+1)-(N-A+1)]
  #Here, A=6 P=8, N=7 -> need element 8*(7+1)-(7-6+1) = 62

  class_6_err_imbalanced <- hh_imbalanced_metrics@metrics$cm$prediction_error_by_class[6]
  class_6_err_balanced <- hh_balanced_metrics@metrics$cm$prediction_error_by_class[6]

  if (class_6_err_imbalanced < class_6_err_balanced) {
      print("--------------------")
      print("")
      print("FAIL, balanced error greater than imbalanced error")
      print("")
      print("")
      print("class_6_err_imbalanced")
      print(class_6_err_imbalanced)
      print("")
      print("class_6_err_balanced")
      print(class_6_err_balanced)
      print("")
      print("--------------------")
  }
  checkTrue(class_6_err_imbalanced >= class_6_err_balanced, "balance_classes makes it worse!")

  testEnd()
}

doTest("gbm imbalanced", test.gbm.imbalanced)
