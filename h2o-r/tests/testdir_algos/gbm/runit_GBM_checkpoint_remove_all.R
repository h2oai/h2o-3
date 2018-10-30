setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



checkpoint.remove.all <- function() {

  iris <- h2o.importFile(locate("smalldata/iris/iris.csv"))
  m1 <- h2o.gbm(x=1:4, y=5, training_frame=iris, ntrees=100)

  path <- h2o.saveModel(m1, path = file.path(sandbox(), "gbm_checkpoint"), force=TRUE)

  print(path)
  h2o.removeAll()
  restored <- h2o.loadModel(path)

  # update m1 with new training data
  iris <- h2o.importFile(locate("smalldata/iris/iris.csv"))
  m2 <- h2o.gbm(x=1:4, y=5, training_frame=iris, ntrees=200, checkpoint=restored@model_id)
}

doTest("GBM checkpoint with remove all", checkpoint.remove.all)
