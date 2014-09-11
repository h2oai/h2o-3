setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

Log.info("Loading R.utils package\n")
if(!"R.utils" %in% rownames(installed.packages())) install.packages("R.utils")
require(R.utils)

test.mnist.manyCols <- function(conn) {
  Log.info("Importing mnist train data...\n")
  train.hex = h2o.uploadFile(conn, locate("../../../smalldata/mnist/train.csv.gz"), "train.hex")
  Log.info("Check that tail works...")
  tail(train.hex)
  tail_ <- tail(train.hex)
  Log.info("Doing gbm on mnist training data.... \n")
  gbm.mnist <- h2o.gbm(x= 1:784, y = 785, data = train.hex, n.trees = 1, interaction.depth = 1, n.minobsinnode = 10, shrinkage = 0.01)
  print(gbm.mnist)

  testEnd()
}

doTest("Many Columns Test: MNIST", test.mnist.manyCols)

