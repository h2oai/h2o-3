setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_171_colname_assign_with_square_brackets <- function() {
  air <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/airlines/allyears2k_headers.zip")), "air")
  print(colnames(air))
  parsed_names <- colnames(air)
  colnames(air)[ncol(air)] <- 'earl'

  print(air)

  print(colnames(air))

  df <- air[,ncol(air)]
  
  expect_that(names(df), equals("earl"))
  
  
}

h2oTest.doTest("PUB-171: Perform colname assign wihth [] and <-", test.pub_171_colname_assign_with_square_brackets)

