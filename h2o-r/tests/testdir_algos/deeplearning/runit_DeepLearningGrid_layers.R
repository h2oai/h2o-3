setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

check.deeplearning.gridlayers <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), key="iris.hex")
  print(summary(iris.hex))
  
  pretty.list <- function(ll) {
    str <- lapply(ll, function(x) { paste("(", paste(x, collapse = ","), ")", sep = "") })
    paste(str, collapse = ",")
  }
  hidden_layers <- list(c(20,20), c(50,50,50))
  Log.info(paste("Deep Learning grid search over hidden layers:", pretty.list(hidden_layers)))
  hh <- h2o.deeplearning(x=1:4, y=5, data=iris.hex, hidden = hidden_layers)
  expect_equal(length(hh@model), 2)
  
  hh_params <- lapply(hh@model, function(x) { x@model$params$hidden })
  expect_equal(length(hh_params), length(hidden_layers))
  expect_true(all(hh_params %in% hidden_layers))

  
  cat("\n\n HH_PARAMS:")

  print(hh_params)
  
  cat("\n\n HIDDEN LAYERS:")
  
  print(hidden_layers)
  
  expect_true(all(hh_params %in% hidden_layers))
  print(hh)
  
  testEnd()
}

doTest("Deep Learning Grid Search: Hidden Layers", check.deeplearning.gridlayers)

