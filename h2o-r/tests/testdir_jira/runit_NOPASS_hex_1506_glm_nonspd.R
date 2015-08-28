######################################################################
# Test for HEX-1506
# h2o.glm() should return warning if matrix is non-SPD
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

heading("BEGIN TEST")
check.hex_1506 <- function(conn) {

  path = locate("smalldata/iris/iris_wheader.nonspd.csv")
  iris.hex = h2o.importFile(path, destination_frame="iris.hex")

  expect_warning(h2o.glm(x = c(1:4,6:8), y = "class_REC", training_frame = iris.hex, family = "binomial", lambda = 0))
  expect_warning(h2o.glm(x = c(1:4,6:8), y = "class_REC", training_frame = iris.hex, family = "binomial", lambda = c(0,1e-5,0.1)))

  testEnd()
}

doTest("HEX-1506", check.hex_1506)
