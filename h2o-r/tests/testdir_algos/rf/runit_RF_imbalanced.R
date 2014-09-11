setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.rf.imbalanced <- function(conn) {
  covtype = h2o.uploadFile(conn, locate("smalldata/covtype/covtype.20k.data"))

  hh_imbalanced=h2o.randomForest(x=c(1:54),y=55,ntree=50,data=covtype,balance.classes=F,nfolds=10, type = "BigData")
  print(hh_imbalanced)
  hh_balanced=h2o.randomForest(x=c(1:54),y=55,ntree=50,data=covtype,balance.classes=T,nfolds=10, type = "BigData")
  print(hh_balanced)

  #compare error for class 6 (difficult minority)
  #confusion_matrix element at position A,P for N classes is at: model$confusion[P*(N+1)-(N-A+1)]
  #Here, A=6 P=8, N=7 -> need element 8*(7+1)-(7-6+1) = 62

  class_6_err_imbalanced = hh_imbalanced@model$confusion[62]
  class_6_err_balanced = hh_balanced@model$confusion[62]

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
  checkTrue(class_6_err_imbalanced >= 0.9*class_6_err_balanced, "balance_classes makes it at least 10% worse!")

  testEnd()
}

doTest("rf imbalanced", test.rf.imbalanced)
