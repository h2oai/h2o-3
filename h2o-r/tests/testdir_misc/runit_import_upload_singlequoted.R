setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")


test.import_single_quoted <- function() {
  path <- locate("smalldata/parser/single_quotes_mixed.csv")   
  hdf <- h2o.importFile(
      path = path,
      quotechar = "'"
  )
  expect_true(h2o.ncol(hdf) == 20)
  expect_true(h2o.nrow(hdf) == 7)
    
  df <- read.csv(path, quote="'", stringsAsFactors = TRUE)
  hddf <- as.data.frame(hdf)
  # comparing last column only as it's difficult to compare dataframes in R (always cryptic errors on some column): 
    # if parsing was ok, last column should be identical, otherwise it should be shifted
  expect_equal(df['status'], hddf['status'])
}
    
test.upload_single_quoted <- function() {
    path <- locate("smalldata/parser/single_quotes_mixed.csv")
    hdf <- h2o.uploadFile(
      path = path,
      quotechar =  "'"
    )
  expect_true(h2o.ncol(hdf) == 20)
  expect_true(h2o.nrow(hdf) == 7)
    
  df <- read.csv(path, quote="'", stringsAsFactors = TRUE)
  hddf <- as.data.frame(hdf)
  expect_equal(df['status'], hddf['status'])
}

test.import_fails_on_unsupported_quotechar <- function() {
  expect_error({
    h2o.importFile(
      path = locate("smalldata/parser/single_quotes_mixed.csv"),
      quotechar = "f"
    )
    stop("Incorrect quote character should not have been accepted by importFile")
  }, "`quotechar` must be either NULL or single \\('\\) or double \\(\"\\) quotes")
}

test.upload_fails_on_unsupported_quotechar <- function() {
  expect_error({
    h2o.uploadFile(
      path = locate("smalldata/parser/single_quotes_mixed.csv"),
      quotechar = "f"
    )
    stop("Incorrect quote character should not have been accepted by uploadFile")
  }, "`quotechar` must be either NULL or single \\('\\) or double \\(\"\\) quotes")
}

test.upload_custom_escape <- function() {
    customDataURL = locate("smalldata/parser/single_quotes_with_escaped_quotes_custom_escapechar.csv")
    custom = h2o.importFile(path = customDataURL, destination_frame = "customDataURL.hex", quotechar="'", escapechar='*')
    defaultDataURL = locate("smalldata/parser/single_quotes_with_escaped_quotes.csv")
    default = h2o.importFile(path = defaultDataURL, destination_frame = "defaultDataURL.hex", quotechar="'", , escapechar='\\')
    
    expect_equal(custom['carnegie_basic_classification'], default['carnegie_basic_classification'])
}

doSuite("Test single quotes import/upload file", makeSuite(
    test.import_single_quoted,
    test.upload_single_quoted,
    test.import_fails_on_unsupported_quotechar,
    test.upload_fails_on_unsupported_quotechar,
    test.upload_custom_escape
))
