setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

h2o_init_test <- function() {

  
  e <- tryCatch({
    h2o.init(
      strict_version_check = FALSE,
      ip = "127.0.0.1",
      port = 12345,
      https = TRUE
    )
  }, error = function(x) x)
  
  expect_false(is.null(e))
  print(e)
  err_message <- e[[1]]
  expect_true("Unable to start local server with https enabled. Consider disabling https." == err_message)
}

doTest("Error thrown when HTTPS is enabled on h2o.init() (local cluster)",
       h2o_init_test)
