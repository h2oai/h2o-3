setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_1706 <- function() {
  covtype = h2o.importFile(path=h2oTest.locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] = as.factor(covtype[,55])
  hh_balanced = h2o.gbm(x=1:54, y=55, ntrees=10, training_frame=covtype, balance_classes=TRUE, nfolds=3, distribution="multinomial")
  hh_balanced = h2o.randomForest(x=1:54, y=55, ntrees=10, training_frame=covtype, balance_classes=TRUE, nfolds=3)

  
}

h2oTest.doTest("PUBDEV-1706", test.pubdev_1706)
