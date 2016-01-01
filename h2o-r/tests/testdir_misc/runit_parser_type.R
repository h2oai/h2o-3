setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#'
#' Test parser_type parameter in R
#'



test.parser_type <- function() {

  hex <- h2o.uploadFile(h2oTest.locate("smalldata/synthetic/syn_binary_100x3000.svm"), "svm_data", parse_type = "SVMLight")

  hex.csv <- h2o.uploadFile(h2oTest.locate("smalldata/synthetic/syn_binary_100x3000.svm"), "svm_data_as_csv", parse_type = "CSV")

  print(hex)

  print(hex.csv)

  
}

h2oTest.doTest("Test parser_type", test.parser_type)
