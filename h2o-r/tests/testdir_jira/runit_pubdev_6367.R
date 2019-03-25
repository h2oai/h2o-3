setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.nondatalinemarkers <- function() {
  
  # Use default
  tmp = tempfile(fileext = ".tsv")
  data <- data.frame("C1" = c(1,2,'3',4,5), "C2" = c(6:10), "C3" = c(11:15))
  write.table(data, file=tmp, quote=FALSE, sep='\t', col.names = FALSE, row.names = FALSE)
  imported <- h2o.importFile(tmp, col.types = c("String", "Numeric", "Numeric"))
  expect_equal(5, h2o.nrow(imported))
  
  #Override default with empty list
  tmp = tempfile(fileext = ".tsv")
  data <- data.frame("C1" = c(1,2,'#3',4,5), "C2" = c(6:10), "C3" = c(11:15))
  write.table(data, file=tmp, quote=FALSE, sep='\t', col.names = FALSE, row.names = FALSE)
  imported <- h2o.importFile(tmp, col.types = c("String", "Numeric", "Numeric"), custom_non_data_line_markers = '')
  expect_equal(5, h2o.nrow(imported))
  
  # Non-default single non-data line marker
  data <- data.frame("C1" = c(1,2,'@3',4,5), "C2" = c(6:10), "C3" = c(11:15))
  write.table(data, file=tmp, quote=FALSE, sep='\t', col.names = FALSE, row.names = FALSE)
  imported <- h2o.importFile(tmp, col.types = c("String", "Numeric", "Numeric"), custom_non_data_line_markers = '@')
  expect_equal(4, h2o.nrow(imported))
  
  # Multiple non-default non-data line markers 
  data <- data.frame("C1" = c(1,2,'@3','#4',5), "C2" = c(6:10), "C3" = c(11:15))
  write.table(data, file=tmp, quote=FALSE, sep='\t', col.names = FALSE, row.names = FALSE)
  imported <- h2o.importFile(tmp, col.types = c("String", "Numeric", "Numeric"), custom_non_data_line_markers = '@#')
  expect_equal(3, h2o.nrow(imported))
}

doTest("Setting non-data line markers", test.nondatalinemarkers)
