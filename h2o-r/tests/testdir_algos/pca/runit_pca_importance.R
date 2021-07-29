setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.pca.importances <- function() {
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = "DEMEAN")
  expect_equal(fitH2O@model$importance, h2o.varimp(fitH2O))
}

test.pca.screeplot <- function() {
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = "DEMEAN")
  f <- tempfile(fileext = ".pdf")
  tryCatch({
    pdf(f)
    h2o.screeplot(fitH2O)
    dev.off()
    expect_true(file.exists(f))
  }, finally={
    unlink(f)
  })
  }

doSuite("PCA Test: Retrieving/Plotting importances",
    makeSuite(
    test.pca.importances,
    test.pca.screeplot
    ))
