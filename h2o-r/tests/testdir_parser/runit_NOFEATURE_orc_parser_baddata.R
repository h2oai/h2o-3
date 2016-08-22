setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# This test is written to make sure that warnings from Orc Parser are passed to the R client.
# In particular, the first two Orc files contain unsupported column types.
# The third Orc file contains big integer values that are used by sentinel for H2O frame.

test.orc_parser.bad_data <- function() {
  options(warn=1)     # make warnings to cause an error 
  
  # These files contain unsupported data types
  frame = h2o.importFile(locate("smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc"))
  expect_warning(h2o.importFile(locate("smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc")))
  frame = h2o.importFile(locate("smalldata/parser/orc/TestOrcFile.emptyFile.orc"))
  expect_warning(h2o.importFile(locate("smalldata/parser/orc/TestOrcFile.emptyFile.orc")))
  # This file contains big integer value Long.MIN_VALUE that is used for sentinel
  frame = h2o.importFile(locate("smalldata/parser/orc/nulls-at-end-snappy.orc"))
  expect_warning(h2o.importFile(locate("smalldata/parser/orc/nulls-at-end-snappy.orc")))
  
#   b = warnings()    # collect all warnings into a list
#   print(length(b))
#   if (length(b) < 1) {
#      browser()
# #     throw("Not all warning messages are passed from Java to R client.")
#    }
}

doTest("Orc Parser: make sure warnings are passed to user.", test.orc_parser.bad_data)
