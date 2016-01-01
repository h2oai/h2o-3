setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub.1371 <- function() {
  test_frame <- h2o.createFrame(rows = 10000, cols = 200, randomize = TRUE, value = 0, real_range = 37743,
                               categorical_fraction = 0.1, factors = 5, integer_fraction = 0.5, 
                               binary_fraction = 0.1, binary_ones_fraction = 0.02, integer_range = 243943, 
                               missing_fraction = 0.01, response_factors = 2, has_response = FALSE)
  print(summary(test_frame))
  my_pca <- h2o.prcomp(test_frame, k = 19, transform = "STANDARDIZE", max_iterations = 1000, use_all_factor_levels = FALSE)
  print(my_pca)
  
}

h2oTest.doTest("PUBDEV-1371: Row handling must match between Gram and SVD tasks", test.pub.1371)
