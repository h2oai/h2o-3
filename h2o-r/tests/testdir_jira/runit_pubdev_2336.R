setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.pubdev.2336 <- function(conn) {
  h2oTest.logInfo("Importing jira/pubdev_2336.csv")
  fr <- h2o.importFile(h2oTest.locate("smalldata/jira/pubdev_2336.csv"))
  print(summary(fr))
  
  x <- setdiff(names(fr), "response")
  h2oTest.logInfo("PCA with pca_method = 'GramSVD' and impute_missing = FALSE gives error")
  expect_error(model_err <- h2o.prcomp(training_frame = fr, x = x, k = 13, max_iterations = 792, pca_method = "GramSVD", impute_missing = FALSE))
  
  h2oTest.logInfo("PCA with pca_method = 'GramSVD' and impute_missing = TRUE runs to completion")
  model_imp <- h2o.prcomp(training_frame = fr, x = x, k = 13, max_iterations = 792, pca_method = "GramSVD", impute_missing = TRUE)
  print(model_imp)
  
  h2oTest.logInfo("PCA with pca_method = 'GLRM' runs to completion")
  model_glrm <- h2o.prcomp(training_frame = fr, x = x, k = 13, max_iterations = 792, pca_method = "GLRM", use_all_factor_levels = TRUE)
  print(model_glrm)
}

h2oTest.doTest("PUBDEV-2336: Expect error if too many missing values in PCA", test.pubdev.2336)
