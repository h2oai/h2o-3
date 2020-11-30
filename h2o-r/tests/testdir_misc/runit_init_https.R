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
  expect_true("Starting local server is not available with https enabled. You may start local instance of H2O with https manually (https://docs.h2o.ai/h2o/latest-stable/h2o-docs/welcome.html#new-user-quick-start)." == err_message)
}

doTest("Error thrown when HTTPS is enabled on h2o.init() (local cluster)",
       h2o_init_test)
