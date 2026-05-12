setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.parseExportZSTD<- function() {
  f1 <- h2o.importFile(locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))

  target <- file.path(sandbox(), "gaussian_20cols_10000Rows.csv.zst")
  h2o.exportFile(f1, target)

  f2 <- h2o.importFile(target)
  compareFrames(f1, f2, prob=1)
}

doTest("Test ZSTD parser and export", test.parseExportZSTD)
