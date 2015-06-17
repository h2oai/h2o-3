#'
#' Test parser_type parameter in R
#'
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.parser_type <- function(conn) {

  hex <- h2o.uploadFile(conn, locate("smalldata/syn_binary_100x3000.svm"), "svm_data", parser_type = "SVMLight")

  hex.csv <- h2o.uploadFile(conn, locate("smalldata/syn_binary_100x3000.svm"), "svm_data_as_csv", parser_type = "CSV")

  print(hex)

  print(hex.csv)

  testEnd()
}

doTest("Test parser_type", test.parser_type)
