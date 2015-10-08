################################################################################
##
## Verifying that R can define features as categorical or continuous on import
##
################################################################################
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.continuous.or.categorical <- function() {
  df.hex <- h2o.uploadFile(locate("smalldata/jira/hexdev_29.csv"),
    col.types = c("enum", "enum", "enum"))

  expect_true(is.factor(df.hex$h1))
  expect_true(is.factor(df.hex$h2))
  expect_true(is.factor(df.hex$h3))


  ##### single file #####
  # col.types as named list
  df.hex <- h2o.importFile(locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5"), col.types=list(C4="Enum"))
  expect_true(length(setdiff(names(df.hex),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex$C1))
  expect_true(is.numeric(df.hex$C2))
  expect_true(is.numeric(df.hex$C3))
  expect_true(is.factor(df.hex$C4))
  expect_true(is.factor(df.hex$C5))

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/iris.csv"), col.types=list("Numeric","Numeric","Numeric","Numeric","Enum")), error = function(x) x)
  expect_true(e[[1]] == "col.types must be named list")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/iris.csv"), col.types=list(C4="Enum")), error = function(x) x)
  expect_true(e[[1]] == "if col.types is a named list, then col.names must be specified")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5"), col.types=list(C6="Enum")), error = function(x) x)
  expect_true(e[[1]] == "names specified in col.types must be a subset of col.names")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5","C6"), col.types=list(C6="Enum")), error = function(x) x)
  expect_true(e[[1]] == "length of col.names must equal to the number of columns in dataset")

  # col.types as character vector
  df.hex <- h2o.importFile(locate("smalldata/iris/iris.csv"), col.names=c("C1","C2","C3","C4","C5"), col.types=c("Numeric","Numeric","Numeric","Numeric","Enum"))
  expect_true(length(setdiff(names(df.hex),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex$C1))
  expect_true(is.numeric(df.hex$C2))
  expect_true(is.numeric(df.hex$C3))
  expect_true(is.numeric(df.hex$C4))
  expect_true(is.factor(df.hex$C5))

  ##### folder #####
  # col.types as named list
  df.hex <- h2o.importFile(locate("smalldata/iris/multiple_iris_files"), col.names=c("C1","C2","C3","C4","C5"), col.types=list(C4="Enum"))
  expect_true(length(setdiff(names(df.hex),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex$C1))
  expect_true(is.numeric(df.hex$C2))
  expect_true(is.numeric(df.hex$C3))
  expect_true(is.factor(df.hex$C4))
  expect_true(is.factor(df.hex$C5))

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files"), col.types=list("Numeric","Numeric","Numeric","Numeric","Enum")), error = function(x) x)
  expect_true(e[[1]] == "col.types must be named list")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files"), col.types=list(C4="Enum")), error = function(x) x)
  expect_true(e[[1]] == "if col.types is a named list, then col.names must be specified")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files"), col.names=c("C1","C2","C3","C4","C5"), col.types=list(C6="Enum")), error = function(x) x)
  expect_true(e[[1]] == "names specified in col.types must be a subset of col.names")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files"), col.names=c("C1","C2","C3","C4","C5","C6"), col.types=list(C6="Enum")), error = function(x) x)
  expect_true(e[[1]] == "length of col.names must equal to the number of columns in dataset")

  # col.types as character vector
  df.hex <- h2o.importFile(locate("smalldata/iris/multiple_iris_files"), col.names=c("C1","C2","C3","C4","C5"), col.types=c("Enum","Numeric","Numeric","Numeric","Enum"))
  expect_true(length(setdiff(names(df.hex),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.factor(df.hex$C1))
  expect_true(is.numeric(df.hex$C2))
  expect_true(is.numeric(df.hex$C3))
  expect_true(is.numeric(df.hex$C4))
  expect_true(is.factor(df.hex$C5))

  ##### folder w/ header#####
  # col.types as named list
  df.hex <- h2o.importFile(locate("smalldata/iris/multiple_iris_files_wheader"), col.names=c("C1","C2","C3","C4","C5"), col.types=list(C3="Enum"))
  expect_true(length(setdiff(names(df.hex),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex$C1))
  expect_true(is.numeric(df.hex$C2))
  expect_true(is.factor(df.hex$C3))
  expect_true(is.numeric(df.hex$C4))
  expect_true(is.factor(df.hex$C5))

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files_wheader"), col.types=list("Numeric","Numeric","Numeric","Numeric","Enum")), error = function(x) x)
  expect_true(e[[1]] == "col.types must be named list")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files_wheader"), col.types=list(C4="Enum")), error = function(x) x)
  expect_true(e[[1]] == "if col.types is a named list, then col.names must be specified")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files_wheader"), col.names=c("C1","C2","C3","C4","C5"), col.types=list(C6="Enum")), error = function(x) x)
  expect_true(e[[1]] == "names specified in col.types must be a subset of col.names")

  e <- tryCatch(h2o.importFile(locate("smalldata/iris/multiple_iris_files_wheader"), col.names=c("C1","C2","C3","C4","C5","C6"), col.types=list(C6="Enum")), error = function(x) x)
  expect_true(e[[1]] == "length of col.names must equal to the number of columns in dataset")

  # col.types as character vector
  df.hex <- h2o.importFile(locate("smalldata/iris/multiple_iris_files_wheader"), col.names=c("C1","C2","C3","C4","C5"), col.types=c("Numeric","Enum","Numeric","Numeric","Enum"), header=T)
  expect_true(length(setdiff(names(df.hex),c("C1","C2","C3","C4","C5"))) == 0)
  expect_true(is.numeric(df.hex$C1))
  expect_true(is.factor(df.hex$C2))
  expect_true(is.numeric(df.hex$C3))
  expect_true(is.numeric(df.hex$C4))
  expect_true(is.factor(df.hex$C5))

}

doTest("Veryfying R Can Declare Types on Import", test.continuous.or.categorical)
