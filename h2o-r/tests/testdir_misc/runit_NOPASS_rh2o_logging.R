##
# Test: Logging H2O from R
# Description: Capture POST commands sent from R and corresponding HTTP response.
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rh2o_logging <- function(conn) {
  # Change log paths to R working directory
  h2o.setLogPath(getwd(), "Command")
  h2o.setLogPath(getwd(), "Error")

  cmd_path <- h2o.getLogPath("Command")
  err_path <- h2o.getLogPath("Error")

  Log.info(cat("Command logs saved to", cmd_path, "\n"))
  Log.info(cat("Error logs saved to", err_path, "\n"))

  Log.info("Begin logging..."); h2o.startLogging()
  Log.info("Import iris dataset, then run summary and GBM")
  iris.hex <- h2o.uploadFile(path = locate("smalldata/iris/iris_wheader.csv"), key = "iris.hex")
  print(summary(iris.hex))
  iris.gbm <- h2o.gbm(x = 1:4, y = 5, data = iris.hex)
  print(iris.gbm)
  Log.info("Stop logging..."); h2o.stopLogging()

  print(file.info(cmd_path))
  print(file.info(err_path))

  Log.info("Deleting all logs..."); h2o.clearLogs()
  expect_false(file.exists(cmd_path))
  expect_false(file.exists(err_path))

  testEnd()
}

doTest("Logging Tests: h2o.startLogging, h2o.stopLogging", test.rh2o_logging)
