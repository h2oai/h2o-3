setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.pca.implementations <- function() {
  Log.info("Importing arrests.csv data...") 
  arrestsH2O <- h2o.uploadFile(
    locate("smalldata/pca_test/USArrests.csv"),
    destination_frame = "arrestsH2O")
  
  Log.info("Testing to see whether the trained PCA are essentially the same using different implementation...")
  # Obtain eigenvectors for PCA trained using different implementations
  eigenvectors <- lapply(
    c("MTJ_EVD_DENSEMATRIX", "MTJ_EVD_SYMMMATRIX", "MTJ_SVD_DENSEMATRIX", "JAMA"),
    function(impl) {
      Log.info(paste0("Run PCA with implementation: ", impl))
      model <- h2o.prcomp(
        arrestsH2O,
        k = 4,
        pca_implementation = impl,
        seed = 1234)
      model@model$eigenvectors
    })
  
  # Compare to see if they are fundamentally the same
  invisible(lapply(names(eigenvectors[[1]]), function(pc_ind) {
    eigenvector_standard <- abs(eigenvectors[[1]][[pc_ind]])
    lapply(eigenvectors, function(x) expect_equal(abs(x[[pc_ind]]), eigenvector_standard))
  }))
}

doTest("PCA Test: Different PCA Implementations", test.pca.implementations)
