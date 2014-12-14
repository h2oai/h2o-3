setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

Log.info("Loading R.utils package\n")
if(!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils")
require(R.utils)

test.mnist.manyCols <- function(conn) {
  Log.info("Importing mnist train data...\n")
  train.hex <- h2o.uploadFile(conn, locate("bigdata/laptop/mnist/train.csv.gz"), "train.hex")
  Log.info("Check that tail works...")
  tail(train.hex)
  tail_ <- tail(train.hex)
  Log.info("Doing gbm on mnist training data.... \n")
  gbm.mnist <- h2o.gbm(x= 1:784, y = 785, training_frame = train.hex, ntrees = 1, max_depth = 1, min_rows = 10, learn_rate = 0.01)
  print(gbm.mnist)

  testEnd()
}

doTest("Many Columns Test: MNIST", test.mnist.manyCols)

