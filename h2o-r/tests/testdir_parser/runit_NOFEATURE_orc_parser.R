setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# This simple test is used to make sure that orc file parsing works across the REST
# API for R clients.

test.orc_parser <- function(){
  Options(warn=1)
  # all orc files that Tom K has found
  allOrcFiles = c("smalldata/parser/orc/TestOrcFile.columnProjection.orc",
      "smalldata/parser/orc/bigint_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.emptyFile.orc",
      "smalldata/parser/orc/bool_single_col.orc",
      "smalldata/parser/orc/demo-11-zlib.orc",
      "smalldata/parser/orc/TestOrcFile.testDate1900.orc",
      "smalldata/parser/orc/demo-12-zlib.orc",
      "smalldata/parser/orc/TestOrcFile.testDate2038.orc",
      "smalldata/parser/orc/double_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testMemoryManagementV11.orc",
      "smalldata/parser/orc/float_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testMemoryManagementV12.orc",
      "smalldata/parser/orc/int_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testPredicatePushdown.orc",
      "smalldata/parser/orc/nulls-at-end-snappy.orc",
      "smalldata/parser/orc/TestOrcFile.testSnappy.orc",
      "smalldata/parser/orc/orc_split_elim.orc",
      "smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc",
      "smalldata/parser/orc/TestOrcFile.testStripeLevelStats.orc",
      "smalldata/parser/orc/smallint_single_col.orc",
      "smalldata/parser/orc/string_single_col.orc",
      "smalldata/parser/orc/tinyint_single_col.orc",
      "smalldata/parser/orc/TestOrcFile.testWithoutIndex.orc")

  for (temp in 1:length(allOrcFiles)) {
    h2oFrame = h2o.importFile(locate(allOrcFiles[temp]))
  }

}

doTest("Orc parser Test", test.orc_parser )
