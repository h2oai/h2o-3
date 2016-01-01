setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.slice.colTypes <- function() {
  h2oTest.logInfo("Importing iris.csv data...")
  iris.hex = h2o.importFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
  h2oTest.logInfo("Check that summary works...")
  print(summary(iris.hex))
  summary_ <- summary(iris.hex) #keep the summary around
  iris_nrows <- nrow(iris.hex)
  iris_ncols <- ncol(iris.hex)
  
  h2oTest.logInfo("Check that iris is 150x5")
  h2oTest.logInfo(cat("Got:\n",iris_nrows, iris_ncols))
  expect_that(iris_nrows, equals(150))
  expect_that(iris_ncols, equals(5))
  h2oTest.logInfo("Check the column data types: \nExpect 'double, double, double, double, integer'")

  col1_type <- typeof(head(iris.hex[,1],nrow(iris.hex))[,1])
  col2_type <- typeof(head(iris.hex[,2],nrow(iris.hex))[,1])
  col3_type <- typeof(head(iris.hex[,3],nrow(iris.hex))[,1])
  col4_type <- typeof(head(iris.hex[,4],nrow(iris.hex))[,1])
  col5_type <- typeof(head(iris.hex[,5],nrow(iris.hex))[,1])
  h2oTest.logInfo(cat("Got:\n",col1_type,col2_type,col3_type,col4_type,col5_type))
  
  expect_that(col1_type, equals("double"))
  expect_that(col2_type, equals("double"))
  expect_that(col3_type, equals("double"))
  expect_that(col4_type, equals("double"))
  expect_that(col5_type, equals("integer"))

  
}

h2oTest.doTest("Slice Tests: Check Col Types", test.slice.colTypes)

