setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# In this test, I want to make sure importance and scoring history for PCA models built using
# all PCA methods are generated correctly.  In addition, I check and make sure that PCA
# models are built correctly and agree with each other when different transform is used.  When
# the transform is STANDARDIZE, we will compare our PCA models with R PCA model since  R only
# support STANDARDIZE transform.
test.pca.pubdev3827 <- function() {
  Log.info("Importing australia.csv data...\n")
  australia.hex <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"))
  australia.hex <- na.omit(australia.hex) # remove na rows
  aus = as.data.frame(australia.hex)

  transform_types = c("NONE", "STANDARDIZE", "NORMALIZE", "DEMEAN", "DESCALE")
  transformN = transform_types[sample(1:4,1,replace=F)]
  Log.info(paste("transform is set to ", transformN, sep=" "))

  ranks = 3
  if (transformN == "STANDARDIZE") {    # check result with R PCA for standardize
    Log.info("Building PCA model using R...")
    pcaR = prcomp(aus, center=TRUE, scale.=TRUE, rank.=ranks)
  }
  
  Log.info("Building PCA model with GramSVD...")
  gramSVD.pca <- h2o.prcomp(training_frame = australia.hex, transform=transformN, k=ranks)
  print(gramSVD.pca@model$scoring_history)
  print(gramSVD.pca@model$importance)

  Log.info("Building PCA model with Randomized...") 
  randomized.pca <- h2o.prcomp(training_frame = australia.hex, transform=transformN, k=ranks,
  pca_method="Randomized", compute_metrics=TRUE)
  print(randomized.pca@model$scoring_history)
  print(randomized.pca@model$importance)

  Log.info("Building PCA model with Power...") 
  power.pca <- h2o.prcomp(training_frame = australia.hex, transform=transformN, k=ranks,
                               pca_method="Power", compute_metrics=TRUE)
  print(power.pca@model$scoring_history)
  print(power.pca@model$importance)

  Log.info("Building PCA model with GLRM...")
  glrm.pca <- h2o.prcomp(training_frame = australia.hex, transform=transformN, k=ranks,
                               pca_method="GLRM", compute_metrics=TRUE, use_all_factor_levels=TRUE)
  print(glrm.pca@model$scoring_history)
  print(glrm.pca@model$importance)

  if (transformN == "STANDARDIZE") {  # just compare R and H2O PCA models with GramSVD
  Log.info("****** Comparing R PCA model and GramSVD PCA model...")
  isFlipped1 <- checkPCAModel(gramSVD.pca, pcaR, tolerance=3e-2, compare_all_importance=TRUE)
  }

  isFlipped1 <- checkPCAModelWork(ranks, gramSVD.pca@model$importance, randomized.pca@model$importance,
  gramSVD.pca@model$eigenvectors, randomized.pca@model$eigenvectors,
  "Compare importance between PCA GramSVD and PCA Randomized",
  "PCA GramSVD Importance of Components:",
  "PCA Randomized Importance of Components:", tolerance=2e-2,
  compare_all_importance=TRUE)

  Log.info("****** Comparing GramSVD PCA model and Power PCA model...")
  isFlipped1 <- checkPCAModelWork(ranks, gramSVD.pca@model$importance, power.pca@model$importance,
  gramSVD.pca@model$eigenvectors, power.pca@model$eigenvectors,
  "Compare importance between PCA GramSVD and PCA Power",
  "PCA GramSVD Importance of Components:",
  "PCA Power Importance of Components:", tolerance=2e-6,
  compare_all_importance=TRUE)

  Log.info("****** Comparing GramSVD PCA model and GLRM PCA model...")
  isFlipped1 <- checkPCAModelWork(ranks, gramSVD.pca@model$importance, glrm.pca@model$importance, 
                              gramSVD.pca@model$eigenvectors, glrm.pca@model$eigenvectors, 
                              "Compare importance between PCA GramSVD and PCA GLRM", 
                              "PCA GramSVD Importance of Components:", 
                              "PCA GLRM Importance of Components:", tolerance=2e-1,
                              compare_all_importance=TRUE)

  Log.info("Checking scoring history from all PCA methods")
  expect_true(length(gramSVD.pca@model$scoring_history) > 0) # make sure we have a valid scoring history
  expect_true(length(randomized.pca@model$scoring_history) > 0) # make sure we have a valid scoring history
  expect_true(length(power.pca@model$scoring_history) > 0) # make sure we have a valid scoring history
  expect_true(length(glrm.pca@model$scoring_history) > 0) # make sure we have a valid scoring history

  Log.info("Checking importance from all PCA methods")
  expect_true(length(gramSVD.pca@model$importance) > 0) # make sure we have a valid importance
  expect_true(length(randomized.pca@model$importance) > 0) # ake sure we have a valid importance
  expect_true(length(power.pca@model$importance) > 0) # ake sure we have a valid importance
  expect_true(length(glrm.pca@model$importance) > 0) # ake sure we have a valid importance
}

doTest("PCA Test: PUBDEV-3827: Scoring history and importance of components", test.pca.pubdev3827)
