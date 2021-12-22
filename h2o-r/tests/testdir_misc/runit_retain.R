setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.h2o.retain <- function() {
  
  data <- h2o.importFile(path = locate("smalldata/iris/iris_wheader.csv"))
  
  h2o.removeAll(retained_elements = c(data))
  
  expect_false(is.null(h2o.getFrame(h2o.getId(data))))
  
  model <-
    h2o.gbm(
      x = c("sepal_wid", "sepal_len", "petal_wid", "petal_len"),
      y = "class",
      training_frame = data
    )
  
  h2o.removeAll(retained_elements = c(data, model))
  expect_false(is.null(h2o.getFrame(h2o.getId(data))))
  expect_false(is.null(h2o.getModel(model@model_id)))

  h2o.removeAll(retained_elements = c(h2o.getId(data), model@model_id))
  expect_false(is.null(h2o.getFrame(h2o.getId(data))))
  expect_false(is.null(h2o.getModel(model@model_id)))

  h2o.removeAll(retained_elements = c(data, model@model_id))
  expect_false(is.null(h2o.getFrame(h2o.getId(data))))
  expect_false(is.null(h2o.getModel(model@model_id)))

  h2o.removeAll()
  expect_equal(length(h2o.ls()$key), 0)
}

doTest("Test h2o.retain", test.h2o.retain)

