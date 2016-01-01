setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning_imbalanced <- function() {
  h2oTest.logInfo("Test checks if Deep Learning works fine with an imbalanced dataset")
  
  covtype <- h2o.uploadFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] <- as.factor(covtype[,55])
  hh_imbalanced<-h2o.deeplearning(x=c(1:54),y=55,l1=1e-5,epochs=1,training_frame=covtype,balance_classes=F,reproducible=T, seed=12345)
  print(hh_imbalanced)
  hh_balanced<-h2o.deeplearning(x=c(1:54),y=55,l1=1e-5,epochs=1,training_frame=covtype,balance_classes=T,reproducible=T, seed=12345)
  print(hh_balanced)

  #compare overall logloss
  class_6_err_imbalanced <- h2o.logloss(hh_imbalanced)
  class_6_err_balanced <- h2o.logloss(hh_balanced)

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

  
}

h2oTest.doTest("Deep Learning Imbalanced Test", check.deeplearning_imbalanced)
