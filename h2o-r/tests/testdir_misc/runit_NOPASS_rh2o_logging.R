setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test: Logging H2O from R
# Description: Capture POST commands sent from R and corresponding HTTP response.
##




test.rh2o_logging <- function() {

  # Change log paths to R working directory
  h2o.setLogPath(getwd(), "Command")
  h2o.setLogPath(getwd(), "Error")

  cmd_path <- h2o.getLogPath("Command")
  err_path <- h2o.getLogPath("Error")

  h2oTest.logInfo(cat("Command logs saved to", cmd_path, "\n"))
  h2oTest.logInfo(cat("Error logs saved to", err_path, "\n"))

  h2oTest.logInfo("Begin logging..."); h2o.startLogging()
  h2oTest.logInfo("Import iris dataset, then run summary and GBM")
  iris.hex <- h2o.uploadFile(path = h2oTest.locate("smalldata/iris/iris_wheader.csv"), key = "iris.hex")
  print(summary(iris.hex))
  iris.gbm <- h2o.gbm(x = 1:4, y = 5, data = iris.hex)
  print(iris.gbm)
  h2oTest.logInfo("Stop logging..."); h2o.stopLogging()

  print(file.info(cmd_path))
  print(file.info(err_path))

  h2oTest.logInfo("Deleting all logs..."); h2o.clearLogs()
  expect_false(file.exists(cmd_path))
  expect_false(file.exists(err_path))

}

h2oTest.doTest("Logging Tests: h2o.startLogging, h2o.stopLogging", test.rh2o_logging)
