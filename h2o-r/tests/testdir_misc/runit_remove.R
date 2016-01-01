setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test if h2o.rm removes objects from the client
##




test <- function() {
  iris_hex = as.h2o(iris)
  model = h2o.glm(1:4,5,training=iris_hex)
  
  h2o.rm(iris_hex)
  expect_false(exists('iris_hex'))
  
  h2o.rm(model)
  expect_false(exists('model'))
  
  iris_hex = as.h2o(iris)
  model = h2o.glm(1:4,5,training=iris_hex)
  remove_me = c(iris_hex, model)
  h2o.rm(remove_me)
  expect_false(exists('remove_me'))
  
  expect_true(exists('model'))
  h2o.rm(model@model_id)
  expect_true(exists('model'))
}

h2oTest.doTest("Remove objects from the client", test)

