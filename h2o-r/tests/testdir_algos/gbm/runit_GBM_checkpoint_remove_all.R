setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

checkpoint.remove.all <- function(conn) {

  temp_dir = tempdir()

  iris = h2o.importFile(locate("smalldata/iris/iris.csv"))
  m1 = h2o.gbm(x=1:4, y=5, training_frame=iris, ntrees=100)

  path = h2o.saveModel(m1, path=temp_dir, force=TRUE)
  h2o.removeAll(conn)
  restored = h2o.loadModel(conn, path)

  # update m1 with new training data
  iris = h2o.importFile(locate("smalldata/iris/iris.csv"))
  m2 = h2o.gbm(x=1:4, y=5, training_frame=iris, ntrees=200, checkpoint=restored@model_id)

  testEnd()
}

doTest("GBM checkpoint with remove all", checkpoint.remove.all)