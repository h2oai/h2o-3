setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
################################################################################
##
## Verifying that R can define features as categorical or continuous on import
##
################################################################################



test.continuous.or.categorical <- function() {
  df.hex <- h2o.uploadFile(h2oTest.locate("smalldata/jira/hexdev_29.csv"),
    col.types = c("enum", "enum", "enum"))

  expect_true(is.factor(df.hex$h1))
  expect_true(is.factor(df.hex$h2))
  expect_true(is.factor(df.hex$h3))


  ##### single file #####
  # col.types as named list
  df.hex1 <- h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.types=list(by.col.name=c("C4"),types=c("Enum")))
  expect_true(length(setdiff(names(df.hex1),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex1$C1))
  expect_true(is.numeric(df.hex1$C2))
  expect_true(is.numeric(df.hex1$C3))
  expect_true(is.factor(df.hex1$C4))
  expect_true(is.factor(df.hex1$C5))

  expect_error(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"),
                              col.types=list("Numeric","Numeric","Numeric","Numeric","Enum")))

  e <- tryCatch(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.types=list(by.col.name=c("C7"),types=c("Enum"))),
                error = function(x) x)
  expect_true(e[[1]] == "by.col.name must be a subset of the actual column names")

  e <- tryCatch(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5","C6"),
                               col.types=list(by.col.name=c("C4"),types=c("Enum"))), error = function(x) x)
  expect_true(e[[1]] == "length of col.names must equal to the number of columns in dataset")

  # col.types as character vector
  df.hex2 <- h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.types=c("Numeric","Numeric","Enum","Numeric","Enum"))
  expect_true(length(setdiff(names(df.hex2),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex2$C1))
  expect_true(is.numeric(df.hex2$C2))
  expect_true(is.factor(df.hex2$C3))
  expect_true(is.numeric(df.hex2$C4))
  expect_true(is.factor(df.hex2$C5))

  ##### folder #####
  # col.types as named list
  df.hex3 <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files"), col.types=list(by.col.name=c("C4"),types=c("Enum")))
  expect_true(length(setdiff(names(df.hex3),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex3$C1))
  expect_true(is.numeric(df.hex3$C2))
  expect_true(is.numeric(df.hex3$C3))
  expect_true(is.factor(df.hex3$C4))
  expect_true(is.factor(df.hex3$C5))

  expect_error(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"),
                              col.types=list("Numeric","Numeric","Numeric","Numeric","Enum")))

  e <- tryCatch(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.types=list(by.col.name=c("C7"),types=c("Enum"))),
                error = function(x) x)
  expect_true(e[[1]] == "by.col.name must be a subset of the actual column names")

  e <- tryCatch(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5","C6"),
                               col.types=list(by.col.name=c("C4"),types=c("Enum"))), error = function(x) x)
  expect_true(e[[1]] == "length of col.names must equal to the number of columns in dataset")

  # col.types as character vector
  df.hex4 <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files"),
                            col.types=c("Enum","Numeric","Numeric","Numeric","Enum"))
  expect_true(length(setdiff(names(df.hex4),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.factor(df.hex4$C1))
  expect_true(is.numeric(df.hex4$C2))
  expect_true(is.numeric(df.hex4$C3))
  expect_true(is.numeric(df.hex4$C4))
  expect_true(is.factor(df.hex4$C5))

  ##### folder w/ header#####
  # col.types as named list
  df.hex5 <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files_wheader"),
                            col.types=list(by.col.name=c("sepal_wid","petal_len"),types=c("Enum","Enum")))
  expect_true(length(setdiff(names(df.hex5),c("sepal_len","sepal_wid","petal_len","petal_wid","class"))) == 0)
  expect_true(is.numeric(df.hex5$sepal_len))
  expect_true(is.factor(df.hex5$sepal_wid))
  expect_true(is.factor(df.hex5$petal_len))
  expect_true(is.numeric(df.hex5$petal_wid))
  expect_true(is.factor(df.hex5$class))

  expect_error(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"),
                              col.types=list("Numeric","Numeric","Numeric","Numeric","Enum")))

  e <- tryCatch(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.types=list(by.col.name=c("C7"),types=c("Enum"))),
                error = function(x) x)
  expect_true(e[[1]] == "by.col.name must be a subset of the actual column names")

  e <- tryCatch(h2o.importFile(h2oTest.locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5","C6"),
                               col.types=list(by.col.name=c("C4"),types=c("Enum"))), error = function(x) x)
  expect_true(e[[1]] == "length of col.names must equal to the number of columns in dataset")

  # col.types as character vector
  df.hex6 <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files_wheader"), col.names=c("C1","C2","C3","C4","C5"),
                            col.types=c("Numeric","Enum","Numeric","Numeric","Enum"))
  expect_true(length(setdiff(names(df.hex6),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex6$C1))
  expect_true(is.factor(df.hex6$C2))
  expect_true(is.numeric(df.hex6$C3))
  expect_true(is.numeric(df.hex6$C4))
  expect_true(is.factor(df.hex6$C5))

  df.hex6 <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files_wheader"),
                            col.types=list(by.col.idx=c(2,3),types=c("Enum","Enum")))
  expect_true(length(setdiff(names(df.hex6),c("sepal_len","sepal_wid","petal_len","petal_wid","class"))) == 0)
  expect_true(is.numeric(df.hex6$sepal_len))
  expect_true(is.factor(df.hex6$sepal_wid))
  expect_true(is.factor(df.hex6$petal_len))
  expect_true(is.numeric(df.hex6$petal_wid))
  expect_true(is.factor(df.hex6$class))

  expect_error(h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files_wheader"),
                              col.types=list(by.col.idx=c(2,3), by.col.name=c("sepal_wid","petal_len"),
                                             types=c("Enum","Enum"))))

  expect_error(h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files_wheader"),
                              col.types=list(by.col.idx=c("sepal_wid","petal_len"), types=c("Enum","Enum"))))

  expect_error(h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files_wheader"),
                              col.types=list(by.col.name=c(2,3), types=c("Enum","Enum"))))

  df.hex7 <- h2o.importFile(h2oTest.locate("smalldata/iris/multiple_iris_files"),
                            col.types=list(by.col.name=c("C5"),types=c("String")))

  expect_true(is.numeric(df.hex7$C1))
  expect_true(is.numeric(df.hex7$C2))
  expect_true(is.numeric(df.hex7$C3))
  expect_true(is.numeric(df.hex7$C4))
  expect_false(is.factor(df.hex7$C5))
  expect_false(is.numeric(df.hex7$C5))

}

h2oTest.doTest("Veryfying R Can Declare Types on Import", test.continuous.or.categorical)

