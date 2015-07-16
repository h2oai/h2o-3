setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev_1706 <- function(conn) {
  covtype = h2o.importFile(path=locate("smalldata/covtype/covtype.20k.data"))
  covtype[,55] = as.factor(covtype[,55])
  hh_balanced = h2o.gbm(x=1:54, y=55, ntrees=50, training_frame=covtype, balance_classes=TRUE, nfolds=10, distribution="multinomial")
  hh_balanced = h2o.random_forest(x=1:54, y=55, ntrees=50, training_frame=covtype, balance_classes=TRUE, nfolds=10)

  testEnd()
}

doTest("PUBDEV-1705", test.pubdev_1706)