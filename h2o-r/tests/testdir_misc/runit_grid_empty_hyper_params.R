setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.grid.empty.hyper.parameter.space <- function(conn) {

  iris <- h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"))

  # gbm
  g <- h2o.grid("gbm",training_frame=iris, x=1:4, y=5)
  print("gbm: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  # glm
  g <- h2o.grid("glm",training_frame=iris, x=1:3, y=4)
  print("glm: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  # drf
  g <- h2o.grid("randomForest",training_frame=iris, x=1:4, y=5)
  print("drf: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  # drf
  g <- h2o.grid("deeplearning",training_frame=iris, x=1:4, y=5)
  print("deeplearning: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  # kmeans
  g <- h2o.grid("kmeans",training_frame=iris, x=1:4, k=3)
  print("kmeans: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  # naivebayes
  g <- h2o.grid("naivebayes",training_frame=iris, x=1:4, y=5)
  print("naivebayes: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  # pca
  g <- h2o.grid("pca",training_frame=iris, x=1:4, k=3)
  print("pca: ")
  print(g)
  expect_equal(length(g@model_ids),1)

  
}

h2oTest.doTest("All grid algos with empty hyper parameter space", test.grid.empty.hyper.parameter.space)
