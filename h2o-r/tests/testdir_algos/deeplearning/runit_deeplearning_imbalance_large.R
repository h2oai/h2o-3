setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_imbalanced <- function() {
  Log.info("Test checks if Deep Learning works fine with an imbalanced dataset")
  
  covtype <- h2o.uploadFile(locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])
  hh_imbalanced<-h2o.deeplearning(x=c(1:54),y=55,l1=1e-5,activation="Rectifier",loss="CrossEntropy",hidden=c(200,200),epochs=1,training_frame=covtype,balance_classes=F,reproducible=T, seed=1234)
  print(hh_imbalanced)
  hh_balanced<-h2o.deeplearning(x=c(1:54),y=55,l1=1e-5,activation="Rectifier",loss="CrossEntropy",hidden=c(200,200),epochs=1,training_frame=covtype,balance_classes=T,reproducible=T, seed=1234)
  print(hh_balanced)

  #compare error for class 6 (difficult minority)
  class_6_err_imbalanced <- h2o.confusionMatrix(hh_imbalanced)[6,8]
  class_6_err_balanced   <- h2o.confusionMatrix(hh_balanced)[6,8]

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

doTest("Deep Learning Imbalanced Test", check.deeplearning_imbalanced)
