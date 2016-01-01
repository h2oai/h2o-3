setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



checkpoint.new.category.in.predictor <- function() {

  sv1 = h2o.uploadFile(h2oTest.locate("smalldata/iris/setosa_versicolor.csv"))
  sv2 = h2o.uploadFile(h2oTest.locate("smalldata/iris/setosa_versicolor.csv"))
  vir = h2o.uploadFile(h2oTest.locate("smalldata/iris/virginica.csv"))

  m1 = h2o.gbm(x=c(1,2,3,5), y=4, training_frame=sv1, ntrees=100)

  m2 = h2o.gbm(x=c(1,2,3,5), y=4,training_frame=sv2, ntrees=200, checkpoint=m1@model_id)

  # attempt to continue building model, but with an expanded categorical predictor domain.
  # this should fail until we figure out proper behavior
  expect_error(m3 <- h2o.gbm(x=c(1,2,3,5), y=4, training_frame=vir, ntrees=200, checkpoint=m1@model_id))

  
}

h2oTest.doTest("GBM checkpoint with new categoricals", checkpoint.new.category.in.predictor )
