setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")


test.upload.import.singlequotes <- function() {
  imported_frame <-
    h2o.importFile(
      path = locate("smalldata/parser/single_quotes_mixed.csv"),
      quotechar = "'"
    )
  expect_true(h2o.ncol(imported_frame) == 20)
  expect_true(h2o.nrow(imported_frame) == 7)
  
  uploaded_frame <-
    h2o.uploadFile(
      path = locate("smalldata/parser/single_quotes_mixed.csv"),
      quotechar =  "'"
    )
  expect_true(h2o.ncol(uploaded_frame) == 20)
  expect_true(h2o.nrow(uploaded_frame) == 7)
  
  e <- tryCatch({
    h2o.importFile(
      path = locate("smalldata/parser/single_quotes_mixed.csv"),
      quotechar = "f"
    )
    stop("Incorrect quote character accepted by importFile")
  }, error = function(err) {
    print(err)
  })
  
  e <- tryCatch({
    h2o.uploadFile(
      path = locate("smalldata/parser/single_quotes_mixed.csv"),
      quotechar = "f"
    )
    stop("Incorrect quote character accepted by uploadFile")
  }, error = function(err) {
    print(err)
  })
  
  
}

doTest("Test single quotes import/upload file",
       test.upload.import.singlequotes)
