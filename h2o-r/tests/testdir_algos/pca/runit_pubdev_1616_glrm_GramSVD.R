setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Nidhi reports that GLRM and GramSVD produce different singular values with the birds.csv
test.pca.glrm_GramSVD <- function() {
  ranks = 5
  tolerance = 1e-1  # relaxe tolerance to accomodate bad randomly generated datasets.
  df <- h2o.importFile(locate("smalldata/pca_test/birds.csv"), destination_frame="df")
  pcaGLRM <- h2o.prcomp(df, k=ranks, use_all_factor_levels=TRUE, pca_method="GLRM", transform="STANDARDIZE")
  pcaGramSVD <- h2o.prcomp(df, k=ranks, use_all_factor_levels=TRUE, pca_method="GramSVD", transform="STANDARDIZE")

  t = pcaGLRM@model$importance
  glrmSVals = c(t$pc1[1], t$pc2[1], t$pc3[1], t$pc4[1], t$pc5[1])
  t = pcaGramSVD@model$importance
  gramSVDSVals = c(t$pc1[1], t$pc2[1], t$pc3[1], t$pc4[1], t$pc5[1])
  maxDiff = max(abs(glrmSVals-gramSVDSVals))
  if (maxDiff > tolerance) {
    print("Eigenvalues/Eigenvectors returned by GLRM and GramSVD differ due to different enum column handling
    and NA rows handling.")
    print("Maximum difference is ")
    print(maxDiff)
  }
  # compare GLRM and GramSVD with numerical dataset.  Should be close.
  df = h2o.createFrame(rows=1000, cols=10, real_range=100,string_fraction=0, categorical_fraction = 0,
  integer_fraction = 0, binary_fraction=0, missing_fraction=0, has_response=FALSE)
  pcaGLRM <- h2o.prcomp(df, k=ranks, use_all_factor_levels=TRUE, pca_method="GLRM", transform="STANDARDIZE")
  pcaGramSVD <- h2o.prcomp(df, k=ranks, use_all_factor_levels=TRUE, pca_method="GramSVD", transform="STANDARDIZE")
  # compare results from GLRM and GramSVD for all numerical data.  Should be the same.
  isFlipped1 <- checkPCAModelWork(ranks, pcaGLRM@model$importance, pcaGramSVD@model$importance,
  pcaGLRM@model$eigenvectors, pcaGramSVD@model$eigenvectors,
  "Compare importance between PCA GLRM and PCA GramSVD",
  "PCA GLRM Importance of Components:",
  "PCA GramSVD Importance of Components:", tolerance=tolerance,
  compare_all_importance=TRUE)

}

doTest("PCA Test: compare SVD from GLRM and GramSVD", test.pca.glrm_GramSVD)
